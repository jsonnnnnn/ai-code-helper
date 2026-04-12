package com.example.aicodehelper.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 基于 PostgreSQL 全文检索（GIN 索引 + ts_rank_cd）的 BM25 近似检索存储。
 *
 * <p>每条记录对应一个 child chunk，通过 {@code websearch_to_tsquery} 解析自然语言查询，
 * {@code ts_rank_cd}（coverage density）提供近 BM25 的排名效果。
 *
 * <p><b>中文分词方案</b>：PostgreSQL 'simple' 分词器无法感知中文词边界，连续汉字会被当作
 * 一个整体 token（如"如何学习" = 1 token），导致查询"如何学"无法命中"如何学习"。
 * 本实现在写入和查询时均对文本做字符级规范化（{@link #normalize}）：
 * <ul>
 *   <li>每个 CJK 字符前后插入空格，使其成为独立 token；</li>
 *   <li>字母与数字边界处插入空格（如 java8 → java 8），避免复合 token 无法匹配。</li>
 * </ul>
 * 若需更高质量的中文 BM25，可安装 pg_jieba / zhparser 扩展并替换文本搜索配置。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class BM25ChildStore {

    static final String TABLE = "rag_bm25_child_chunks";

    private final JdbcTemplate jdbc;

    public BM25ChildStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void createTableIfNotExists() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rag_bm25_child_chunks (
                    id        VARCHAR(36) PRIMARY KEY,
                    parent_id VARCHAR(36) NOT NULL,
                    content   TEXT        NOT NULL,
                    source    VARCHAR(500),
                    created_at TIMESTAMP  DEFAULT NOW()
                )
                """);
        // 迁移：旧版本使用 GENERATED ALWAYS 列，该列无法手动写入规范化文本；
        // 若检测到 GENERATED 列则删除，后续以普通列重建。
        jdbc.execute("""
                DO $$ BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name   = 'rag_bm25_child_chunks'
                          AND column_name  = 'content_tsv'
                          AND is_generated = 'ALWAYS'
                    ) THEN
                        ALTER TABLE rag_bm25_child_chunks DROP COLUMN content_tsv;
                    END IF;
                END $$;
                """);
        // 添加普通 tsvector 列（由 insert() 手动写入规范化后的向量，支持中文字符级索引）
        jdbc.execute("""
                DO $$ BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name  = 'rag_bm25_child_chunks'
                          AND column_name = 'content_tsv'
                    ) THEN
                        ALTER TABLE rag_bm25_child_chunks
                        ADD COLUMN content_tsv TSVECTOR;
                    END IF;
                END $$;
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS rag_bm25_child_chunks_tsv_idx
                ON rag_bm25_child_chunks USING GIN(content_tsv)
                """);
        log.info("[RAG] BM25 child chunk 表已就绪: {}", TABLE);
    }

    public void dropAndRecreate() {
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE);
        createTableIfNotExists();
        log.info("[RAG] BM25 child chunk 表已清空重建");
    }

    /**
     * 写入时对 content 做字符级规范化，再调用 {@code to_tsvector} 生成索引向量。
     * 原始 content 保留不变，以供 cross-encoder 使用。
     */
    public void insert(String id, String parentId, String content, String source) {
        String normalized = normalize(content);
        jdbc.update(
                "INSERT INTO " + TABLE + " (id, parent_id, content, source, content_tsv) "
                        + "VALUES (?, ?, ?, ?, to_tsvector('simple', ?)) ON CONFLICT (id) DO NOTHING",
                id, parentId, content, source, normalized
        );
    }

    /**
     * BM25 近似检索结果：parent_id + 代表 child 文本 + ts_rank_cd 分数。
     */
    public record BM25Result(String parentId, String childContent, double score) {}

    /**
     * 用 {@code websearch_to_tsquery} 解析自然语言查询（支持 AND/OR/引号短语），
     * 按 ts_rank_cd 降序返回最多 {@code limit} 条命中。
     * 查询文本同样经过 {@link #normalize} 处理，与索引保持一致。
     */
    public List<BM25Result> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        log.debug("[RAG] BM25 规范化查询: [{}] → [{}]", query, normalizedQuery);
        try {
            return jdbc.query("""
                    SELECT parent_id,
                           content,
                           ts_rank_cd(content_tsv, websearch_to_tsquery('simple', ?)) AS score
                    FROM rag_bm25_child_chunks
                    WHERE content_tsv @@ websearch_to_tsquery('simple', ?)
                    ORDER BY score DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new BM25Result(
                            rs.getString("parent_id"),
                            rs.getString("content"),
                            rs.getDouble("score")
                    ),
                    normalizedQuery, normalizedQuery, limit
            );
        } catch (Exception e) {
            log.warn("[RAG] BM25 检索异常，降级为空结果: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isEmpty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        return count == null || count == 0;
    }

    // ── 文本规范化 ─────────────────────────────────────────────────────────────

    /**
     * 对文本做字符级 BM25 规范化，使 PostgreSQL 'simple' 分词器能正确处理中文：
     * <ol>
     *   <li>每个 CJK 字符前后插入空格（每字独立 token）；</li>
     *   <li>字母与数字边界处插入空格（java8 → java 8，避免复合 token 不匹配）；</li>
     *   <li>合并多余空格。</li>
     * </ol>
     * 写入（{@link #insert}）和查询（{@link #search}）均使用同一规范化逻辑，保证对称。
     */
    static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder(text.length() * 2);
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (isCjk(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(c);
                if (i + 1 < chars.length && chars[i + 1] != ' ') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
                 // 字母-数字、数字-字母边界处加空格（如 java8 → java 8）
                 .replaceAll("([a-zA-Z])([0-9])", "$1 $2")
                 .replaceAll("([0-9])([a-zA-Z])", "$1 $2")
                 .replaceAll("\\s+", " ")
                 .trim();
    }

    /** 判断字符是否属于常见 CJK 区段。 */
    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF')   // CJK 统一表意文字
            || (c >= '\u3400' && c <= '\u4DBF')   // CJK 扩展 A
            || (c >= '\uF900' && c <= '\uFAFF')   // CJK 兼容表意文字
            || (c >= '\u3000' && c <= '\u303F')   // CJK 符号和标点
            || (c >= '\uFF00' && c <= '\uFFEF');  // 全角字符
    }
}
