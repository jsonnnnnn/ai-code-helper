package com.example.aicodehelper.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 调用 DashScope {@code gte-rerank} 模型对候选文档进行 cross-encoder 重排。
 *
 * <p>gte-rerank 是联合编码（cross-encoder）架构：将 query 与每段文档拼接后整体编码，
 * 输出细粒度相关性分数，精度显著优于双塔（bi-encoder）余弦相似度。
 *
 * <p>API 格式：
 * <pre>
 * POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-reranking/text-reranking
 * {
 *   "model": "gte-rerank",
 *   "input": { "query": "...", "documents": ["doc0", "doc1", ...] },
 *   "parameters": { "top_n": N, "return_documents": false }
 * }
 * </pre>
 *
 * <p>若 API 调用失败，降级为原始顺序（combinedScore 降序），保证流程不中断。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class CrossEncoderReranker {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
       

    @Value("${langchain4j.community.dashscope.chat-model.api-key}")
    private String apiKey;

    @Value("${app.rag.cross-encoder.model:gte-rerank-v2}")
    private String model;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 重排结果：原始文档下标 + cross-encoder 相关性分数（越高越相关）。
     */
    public record RerankResult(int index, double score) {}

    /**
     * 对候选文档列表进行重排，返回按相关性降序排列的结果。
     *
     * @param query     用户原始查询文本
     * @param documents 候选文档列表（通常为每个 parent 的代表 child 文本）
     * @return 重排后列表，按 relevance_score 降序
     */
    public List<RerankResult> rerank(String query, List<String> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> input = Map.of("query", query, "documents", documents);
            Map<String, Object> params = Map.of(
                    "top_n", documents.size(),
                    "return_documents", false
            );
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", input,
                    "parameters", params
            );

            String responseJson = restClient.post()
                    .uri(RERANK_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode results = objectMapper.readTree(responseJson)
                    .path("output").path("results");

            List<RerankResult> ranked = new ArrayList<>();
            for (JsonNode r : results) {
                ranked.add(new RerankResult(
                        r.get("index").asInt(),
                        r.get("relevance_score").asDouble()
                ));
            }
            ranked.sort(Comparator.comparingDouble(RerankResult::score).reversed());
            log.info("[RAG] Cross-encoder 重排完成，候选 {} 个文档", ranked.size());
            return ranked;

        } catch (Exception e) {
            log.warn("[RAG] Cross-encoder 调用失败，降级为 combinedScore 顺序: {}", e.getMessage());
            // 降级：按原始顺序（调用方已经按 combinedScore 降序排好）
            List<RerankResult> fallback = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                fallback.add(new RerankResult(i, 1.0 - i * 0.01));
            }
            return fallback;
        }
    }
}
