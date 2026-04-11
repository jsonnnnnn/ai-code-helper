package com.example.aicodehelper.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parent-Child 两阶段检索器。
 *
 * <p>检索流程：
 * <ol>
 *   <li>向量化用户问题，在 child 向量表中取 top-{@code topChildResults} 命中。</li>
 *   <li>按 {@code parent_id} 聚合：计算每个 parent 的联合得分 = 命中 child 数 + 最高相似度。</li>
 *   <li>按联合得分降序取 top-{@code topParentResults} 个 parent。</li>
 *   <li>从 {@link ParentChunkStore} 取回完整父块原文，作为 RAG 上下文返回。</li>
 * </ol>
 */
@Slf4j
public class ParentChildContentRetriever implements ContentRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ParentChunkStore parentChunkStore;

    /** 第一阶段：从 child 向量表中取出的最大候选数 */
    private final int topChildResults;
    /** 第二阶段：聚合后最终返回的父块数量 */
    private final int topParentResults;
    /** child 相似度最低阈值，低于此值的命中直接丢弃 */
    private final double minChildScore;

    public ParentChildContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ParentChunkStore parentChunkStore,
            int topChildResults,
            int topParentResults,
            double minChildScore) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.parentChunkStore = parentChunkStore;
        this.topChildResults = topChildResults;
        this.topParentResults = topParentResults;
        this.minChildScore = minChildScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // ── Step 1: 向量化查询，检索 top-N child 片段 ──────────────────────────
        // 先把用户问题转成 embedding，后续的相似度检索都围绕这个向量展开。
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topChildResults)
                .minScore(minChildScore)
                .build();

        List<EmbeddingMatch<TextSegment>> childMatches = embeddingStore.search(request).matches();
        log.info("[RAG] child 检索命中 {} 个片段（top={}, minScore={}）",
                childMatches.size(), topChildResults, minChildScore);

        // ── Step 2: 按 parent_id 聚合，计算联合得分 ────────────────────────────
        // 联合得分 = 命中的 child 数量 + 最高相似度
        // 用 parent_id 聚合 child 命中，避免同一个父块被重复返回。
        // LinkedHashMap 可以保留首次出现的顺序，结果更稳定。
        Map<String, ParentAggregation> aggregation = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> match : childMatches) {
            String parentId = match.embedded().metadata().getString("parent_id");
            if (parentId == null) {
                log.warn("[RAG] 发现无 parent_id 的 child 片段，已跳过（text 前缀: {}）",
                        match.embedded().text().substring(0, Math.min(40, match.embedded().text().length())));
                continue;
            }
            aggregation.computeIfAbsent(parentId, k -> new ParentAggregation())
                    .addMatch(match.score());
        }

        // ── Step 3: 按联合得分重排，取 top-N parent ────────────────────────────
        List<Map.Entry<String, ParentAggregation>> ranked = aggregation.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().combinedScore(), a.getValue().combinedScore()))
                .limit(topParentResults)
                .collect(Collectors.toList());

        log.info("[RAG] 聚合后共 {} 个 parent，返回 top {} 个", aggregation.size(), ranked.size());

        // ── Step 4: 取回父块原文，封装为 Content 返回 ─────────────────────────
        List<Content> contents = new ArrayList<>();
        for (Map.Entry<String, ParentAggregation> entry : ranked) {
            ParentAggregation agg = entry.getValue();
            // 最终返回父块原文，而不是 child 片段，保留更完整的上下文。
            String parentContent = parentChunkStore.getById(entry.getKey());
            if (parentContent == null) {
                log.warn("[RAG] parent_id={} 在 DB 中未找到，已跳过", entry.getKey());
                continue;
            }
            log.debug("[RAG] 选中 parent_id={} | 联合得分={:.3f} (hits={}, maxSim={:.3f})",
                    entry.getKey(), agg.combinedScore(), agg.hitCount, agg.maxSimilarity);
            contents.add(Content.from(TextSegment.from(parentContent)));
        }

        return contents;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 内部辅助类：记录单个 parent 的聚合统计
    // ────────────────────────────────────────────────────────────────────────

    private static class ParentAggregation {
        int hitCount = 0;
        double maxSimilarity = 0.0;

        void addMatch(double score) {
            hitCount++;
            maxSimilarity = Math.max(maxSimilarity, score);
        }

        /** 联合得分 = 命中 child 数 + 最高相似度 */
        double combinedScore() {
            return hitCount + maxSimilarity;
        }
    }
}
