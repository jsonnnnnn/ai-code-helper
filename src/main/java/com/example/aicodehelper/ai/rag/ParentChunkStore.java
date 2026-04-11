package com.example.aicodehelper.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 存储 parent chunk 原文的 JDBC DAO。
 * pgvector 表只存储 child 向量；检索命中后通过 parent_id 从此表取回完整上下文。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class ParentChunkStore {

    static final String TABLE = "rag_parent_chunks";

    private final JdbcTemplate jdbc;

    public ParentChunkStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void createTableIfNotExists() {
        // 启动时确保 parent chunk 表存在，避免首次写入时才暴露建表问题。
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rag_parent_chunks (
                    id      VARCHAR(36) PRIMARY KEY,
                    content TEXT        NOT NULL,
                    source  VARCHAR(500),
                    created_at TIMESTAMP DEFAULT NOW()
                )
                """);
        log.info("[RAG] parent chunk 表已就绪：{}", TABLE);
    }

    /** reingest=true 时调用：清空旧数据后重建表 */
    public void dropAndRecreate() {
        // 这里先删后建，保证表结构和数据一起回到干净状态。
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE);
        createTableIfNotExists();
        log.info("[RAG] parent chunk 表已清空重建");
    }

    public void insert(String id, String content, String source) {
        // 使用 ON CONFLICT 避免重复写入导致异常，保持幂等。
        jdbc.update(
                "INSERT INTO " + TABLE + " (id, content, source) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                id, content, source
        );
    }

    /** 按 parent_id 查回原文；找不到时返回 null */
    public String getById(String id) {
        try {
            // 查询不到时由调用方决定是否跳过，因此这里直接返回 null。
            return jdbc.queryForObject(
                    "SELECT content FROM " + TABLE + " WHERE id = ?",
                    String.class, id
            );
        } catch (Exception e) {
            log.warn("[RAG] 未找到 parent chunk: id={}", id);
            return null;
        }
    }

    public boolean isEmpty() {
        // 用 count 判断表是否已有父块数据，作为是否首次构建索引的依据。
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        return count == null || count == 0;
    }
}
