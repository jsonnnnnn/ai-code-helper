package com.example.aicodehelper.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private DataSource dataSource;   //一定注意要区分本地postgreSQL 5432   和docker里的容器postgreSQL 5433端口 （本项目用的这个）

    @Value("${app.rag.pgvector.table:langchain4j_embeddings}")
    private String tableName;

    @Value("${app.rag.pgvector.dimension:1024}")
    private int dimension;

    /**
     * 是否强制重新向量化并写入，默认 false。
     * 设为 true 时会清空旧表后重新写入，避免重复数据；
     * 日常启动保持 false，直接复用已持久化的向量。
     */
    @Value("${app.rag.reingest:false}")
    private boolean reingest;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(tableName)
                .dimension(dimension)
                .createTable(true)
                // 只有在 reingest=true 时才清空旧表，防止每次启动重复写入
                .dropTableFirst(reingest)
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore) {
        if (reingest) {
            log.info("[RAG] reingest=true，开始重新向量化文档并写入 pgvector...");
            ingestDocuments(embeddingStore);
        } else if (isTableEmpty()) {
            log.info("[RAG] 检测到 pgvector 表为空，首次自动写入向量数据...");
            ingestDocuments(embeddingStore);
        } else {
            log.info("[RAG] pgvector 已有向量数据，跳过 ingest，直接使用持久化存储。");
        }

        // 出现问题：自定义内容查询器的时候，最小得分设置0.75过大，
        // 现象：片段明明成功加入了embeddingStore，但内容查询器没有办法匹配到合适的片段，最终导致RAG失效
        // 解决方案：改成0.5，最终debug时得到了5个检索结果，并且成功自动添加到了提示词中
        // 启示：一定要熟悉流程，这样才利于自己排错；喂给ai一定要给足上下文，但也不要多给
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(qwenEmbeddingModel)
                .maxResults(5)
                .minScore(0.5)
                .build();
    }

    private void ingestDocuments(EmbeddingStore<TextSegment> embeddingStore) {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs");
        DocumentByParagraphSplitter paragraphSplitter = new DocumentByParagraphSplitter(1000, 200);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(paragraphSplitter)
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .embeddingModel(qwenEmbeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(documents);
        log.info("[RAG] 文档向量化完成，共写入 {} 个文档片段。", documents.size());
    }

    private boolean isTableEmpty() {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1) == 0;
            }
        } catch (SQLException e) {
            // 表不存在或其他异常时视为空，触发首次 ingest
            log.warn("[RAG] 检查 pgvector 表是否为空时出错（可能尚未创建），将执行 ingest: {}", e.getMessage());
            return true;
        }
        return true;
    }

}
