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
 * RAG 配置：Hybrid Parent-Child 四阶段检索策略。
 *
 * <ul>
 *   <li><b>Parent chunk</b>（≈600-1200 tokens）：按段落粗切，保留完整语义，原文存入
 *       {@code rag_parent_chunks}。</li>
 *   <li><b>Child chunk</b>（≈120-250 tokens，重叠 20-40 tokens）：在父块内细切，向量化后写入
 *       pgvector，同时写入 {@code rag_bm25_child_chunks} 建立 GIN 全文索引。</li>
 *   <li><b>检索</b>：向量 top-20 与 BM25 top-20 并行，按 parent_id 聚合，联合分粗排。</li>
 *   <li><b>重排</b>：top-20 候选送 DashScope gte-rerank cross-encoder，精排后取 top-10 parent。</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    // ── 字符数估算（中英混合，约 2 chars/token）────────────────────────────────
    private static final int MAX_PARENT_CHARS = 1800;  // ≈ 900 tokens
    private static final int MAX_CHILD_CHARS  = 400;   // ≈ 200 tokens
    private static final int CHILD_OVERLAP    = 60;    // ≈ 30 tokens

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private DataSource dataSource;

    @Resource
    private ParentChunkStore parentChunkStore;

    @Resource
    private BM25ChildStore bm25ChildStore;

    @Resource
    private CrossEncoderReranker crossEncoderReranker;

    @Value("${app.rag.pgvector.table:langchain4j_embeddings}")
    private String tableName;

    @Value("${app.rag.pgvector.dimension:1024}")
    private int dimension;

    @Value("${app.rag.reingest:false}")
    private boolean reingest;

    // ── 检索超参（可在 application.yml 中调整）────────────────────────────────
    @Value("${app.rag.retrieval.top-child-results:20}")
    private int topChildResults;

    @Value("${app.rag.retrieval.reranker-candidates:20}")
    private int rerankerCandidates;

    @Value("${app.rag.retrieval.top-parent-results:5}")
    private int topParentResults;

    @Value("${app.rag.retrieval.min-child-score:0.3}")
    private double minChildScore;

    @Value("${app.rag.retrieval.vector-weight:0.6}")
    private double vectorWeight;

    // ── pgvector：仅存储 child 向量 ───────────────────────────────────────────
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // dropTableFirst 由 contentRetriever 统一控制（与 parent/BM25 表同步清理）
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
        if (reingest) {
            log.info("[RAG] reingest=true，清空全部索引（child向量表、parent表、BM25表），重新构建...");
            clearAllIndexes(embeddingStore);
            ingestDocuments(embeddingStore);
        } else if (parentChunkStore.isEmpty()) {
            log.info("[RAG] parent chunk 表为空，首次自动构建完整索引...");
            ingestDocuments(embeddingStore);
        } else if (bm25ChildStore.isEmpty()) {
            // BM25 表为空但 parent 表有数据（如新版本首次启用 BM25），重建全部索引
            log.info("[RAG] BM25 索引为空（可能是新功能首次启用），重建全部索引...");
            clearAllIndexes(embeddingStore);
            ingestDocuments(embeddingStore);
        } else {
            log.info("[RAG] 检测到完整索引（parent + BM25 + 向量），跳过 ingest。");
        }

        return new ParentChildContentRetriever(
                embeddingStore,
                qwenEmbeddingModel,
                parentChunkStore,
                bm25ChildStore,
                crossEncoderReranker,
                topChildResults,
                rerankerCandidates,
                topParentResults,
                minChildScore,
                vectorWeight
        );
    }

    // ── 清空全部索引（保持三张表同步）────────────────────────────────────────

    private void clearAllIndexes(EmbeddingStore<TextSegment> embeddingStore) {
        new JdbcTemplate(dataSource).execute("TRUNCATE TABLE " + tableName);
        log.info("[RAG] child 向量表 {} 已清空", tableName);
        parentChunkStore.dropAndRecreate();
        bm25ChildStore.dropAndRecreate();
    }

    // ── Parent-Child 两阶段 ingest（同时写入向量表与 BM25 表）───────────────

    private void ingestDocuments(EmbeddingStore<TextSegment> embeddingStore) {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs");
        log.info("[RAG] 加载文档 {} 个，开始 parent-child 切分...", documents.size());

        // 父块：按段落粗切，无重叠，保留段落完整性
        DocumentByParagraphSplitter parentSplitter =
                new DocumentByParagraphSplitter(MAX_PARENT_CHARS, 0);
        // 子块：细切 + 重叠，专用于向量检索与 BM25 检索
        DocumentByParagraphSplitter childSplitter =
                new DocumentByParagraphSplitter(MAX_CHILD_CHARS, CHILD_OVERLAP);

        List<TextSegment> allChildSegments = new ArrayList<>();
        int parentCount = 0;

        for (Document doc : documents) {
            String fileName = doc.metadata().getString("file_name");
            List<TextSegment> parentSegments = parentSplitter.split(doc);

            for (TextSegment parentSeg : parentSegments) {
                String parentId = UUID.randomUUID().toString();

                // ① 持久化父块原文
                parentChunkStore.insert(parentId, parentSeg.text(), fileName);
                parentCount++;

                // ② 在父块内切子块
                Document parentDoc = Document.from(parentSeg.text(), parentSeg.metadata());
                List<TextSegment> children = childSplitter.split(parentDoc);

                for (TextSegment child : children) {
                    child.metadata().put("parent_id", parentId);
                    if (fileName != null) {
                        child.metadata().put("file_name", fileName);
                    }
                    allChildSegments.add(child);

                    // ③ 同步写入 BM25 全文索引表（独立 UUID，不需要与 pgvector ID 对应）
                    bm25ChildStore.insert(UUID.randomUUID().toString(), parentId, child.text(), fileName);
                }
            }
        }

        log.info("[RAG] 切分完成：{} 个 parent，{} 个 child，开始向量化...",
                parentCount, allChildSegments.size());

        // ④ 批量向量化并写入 pgvector
        List<Embedding> embeddings = qwenEmbeddingModel.embedAll(allChildSegments).content();
        embeddingStore.addAll(embeddings, allChildSegments);

        log.info("[RAG] 索引构建完成：{} 个 child 向量已写入 pgvector，BM25 索引同步就绪。",
                allChildSegments.size());
    }
}
