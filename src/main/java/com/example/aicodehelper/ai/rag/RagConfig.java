package com.example.aicodehelper.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG 配置：采用 Parent-Child 两级切分策略。
 *
 * <ul>
 *   <li><b>Parent chunk</b>（600-1200 tokens）：按段落/章节粗切，保留完整上下文，原文存入
 *       {@code rag_parent_chunks} 表。</li>
 *   <li><b>Child chunk</b>（120-250 tokens，重叠 20-40 tokens）：在父块内部细切，携带
 *       {@code parent_id}，向量化后写入 pgvector，专门用于相似度检索。</li>
 *   <li><b>检索</b>：先取 top-20 child，按 parent_id 聚合去重，取 3-6 个 parent。</li>
 *   <li><b>重排</b>：联合得分 = 命中 child 数 + 最高相似度，降序取 top-N 父块作为上下文。</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    // ── 字符数与 token 数的近似换算（中英混合文档）────────────────────────────
    // 1 token ≈ 1.5 chars（中文）/ 4 chars（英文），取折中 ~2 chars/token
    // Parent: 600-1200 tokens → 约 1200-2400 chars，取 MAX_PARENT_CHARS = 1800
    // Child : 120-250 tokens  → 约  240-500  chars，取 MAX_CHILD_CHARS  = 400
    //         重叠 20-40 tokens → 约 40-80 chars，取 CHILD_OVERLAP      = 60
    private static final int MAX_PARENT_CHARS = 1800;
    private static final int MAX_CHILD_CHARS  = 400;
    private static final int CHILD_OVERLAP    = 60;

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private DataSource dataSource;

    @Resource
    private ParentChunkStore parentChunkStore;

    @Value("${app.rag.pgvector.table:langchain4j_embeddings}")
    private String tableName;

    @Value("${app.rag.pgvector.dimension:1024}")
    private int dimension;

    /**
     * true：清表重写（文档首次导入或内容变更时使用）。
     * false：复用已有向量，跳过 ingest。
     */
    @Value("${app.rag.reingest:false}")
    private boolean reingest;

    // ── pgvector：仅存储 child 向量 ───────────────────────────────────────────
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // dropTableFirst 固定为 false：child 向量表的清理由 contentRetriever 统一管理，
        // 确保 child 表与 parent 表始终同步清理，避免孤儿向量堆积。
        // 这里负责创建 pgvector 存储，但不主动删表，以免影响已有索引结构。
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(tableName)
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore) {
        // 先决定是否重建索引，再返回检索器；这样 bean 初始化时就能保证数据状态一致。
        if (reingest) {
            log.info("[RAG] reingest=true，同步清空 child 向量表和 parent 表，重新构建索引...");
            // 先清 child（引用 parent_id），再清 parent，两张表在同一方法内原子清理。
            // 使用 TRUNCATE 速度远快于 DROP/CREATE，且不破坏 pgvector 表结构。
            new JdbcTemplate(dataSource).execute("TRUNCATE TABLE " + tableName);
            log.info("[RAG] child 向量表 {} 已清空", tableName);
            parentChunkStore.dropAndRecreate();
            ingestDocuments(embeddingStore);
        } else if (parentChunkStore.isEmpty()) {
            log.info("[RAG] parent chunk 表为空，首次自动构建索引...");
            ingestDocuments(embeddingStore);
        } else {
            log.info("[RAG] 检测到已有索引数据，跳过 ingest，直接复用持久化向量。");
        }

        return new ParentChildContentRetriever(
                embeddingStore,
                qwenEmbeddingModel,
                parentChunkStore,
                20,   // topChildResults：第一阶段取 top-20 child
                5,    // topParentResults：重排后返回 top-5 parent（3-6 之间）
                0.3   // minChildScore：child 相似度最低阈值
        );
    }

    // ── Parent-Child 两阶段 ingest ─────────────────────────────────────────────

    private void ingestDocuments(EmbeddingStore<TextSegment> embeddingStore) {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs");
        log.info("[RAG] 加载文档 {} 个，开始 parent-child 切分...", documents.size());

        // 父块切分：按段落粗切，每块最大 MAX_PARENT_CHARS 字符，无重叠（保留段落完整性）
        // 这一步尽量保留原始文档结构，便于检索结果返回后仍然可读。
        DocumentByParagraphSplitter parentSplitter =
                new DocumentByParagraphSplitter(MAX_PARENT_CHARS, 0);

        // 子块切分：在父块内部细切，较小块+重叠，专用于向量检索
        // 子块更小、更密集，能提高召回率；重叠则减少跨边界信息丢失。
        DocumentByParagraphSplitter childSplitter =
                new DocumentByParagraphSplitter(MAX_CHILD_CHARS, CHILD_OVERLAP);

        List<TextSegment> allChildSegments = new ArrayList<>();
        int parentCount = 0;

        for (Document doc : documents) {
            String fileName = doc.metadata().getString("file_name");

            // ① 切父块
            List<TextSegment> parentSegments = parentSplitter.split(doc);

            for (TextSegment parentSeg : parentSegments) {
                String parentId = UUID.randomUUID().toString();

                // ② 持久化父块原文
                parentChunkStore.insert(parentId, parentSeg.text(), fileName);
                parentCount++;

                // ③ 以父块内容生成子文档，继承父块元数据
                Document parentDoc = Document.from(parentSeg.text(), parentSeg.metadata());
                List<TextSegment> children = childSplitter.split(parentDoc);

                // ④ 给每个子块注入 parent_id（以及文件名，便于追溯）
            // child 只负责检索，真正展示给模型的是 parent 原文，因此这里要保留关联关系。
                for (TextSegment child : children) {
                    child.metadata().put("parent_id", parentId);
                    if (fileName != null) {
                        child.metadata().put("file_name", fileName);
                    }
                    allChildSegments.add(child);
                }
            }
        }

        log.info("[RAG] 切分完成：{} 个 parent，{} 个 child，开始向量化...",
                parentCount, allChildSegments.size());

        // ⑤ 批量向量化所有子块并写入 pgvector
        List<Embedding> embeddings = qwenEmbeddingModel.embedAll(allChildSegments).content();
        embeddingStore.addAll(embeddings, allChildSegments);

        log.info("[RAG] 向量化完成，已写入 {} 个 child 向量到 pgvector。", allChildSegments.size());
    }
}
