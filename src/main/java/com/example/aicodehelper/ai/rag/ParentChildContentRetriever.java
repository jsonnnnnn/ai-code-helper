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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Hybrid Parent-Child 四阶段检索器：向量 + BM25 混合召回 → parent 聚合 → cross-encoder 重排 → 输出。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li><b>召回层</b>：向量检索 top-{@code topChildResults} child（pgvector）
 *       与 BM25 检索 top-{@code topChildResults} child（PostgreSQL FTS）并行执行。</li>
 *   <li><b>聚合层</b>：按 {@code parent_id} 合并，保留每个 parent 下最强 child 证据；
 *       联合分 = {@code vectorWeight × maxVectorScore + bm25Weight × maxBm25Score}。</li>
 *   <li><b>重排层</b>：取 top-{@code rerankerCandidates} 个 parent，以各自最优 child 文本
 *       喂给 cross-encoder（DashScope gte-rerank），获得细粒度相关性分数。</li>
 *   <li><b>输出层</b>：按 cross-encoder 分数降序取 top-{@code topParentResults} 个 parent 原文。</li>
 * </ol>
 */
@Slf4j
public class ParentChildContentRetriever implements ContentRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ParentChunkStore parentChunkStore;
    private final BM25ChildStore bm25ChildStore;
    private final CrossEncoderReranker crossEncoderReranker;

    /** 向量 / BM25 各自召回的 child 数量上限 */
    private final int topChildResults;
    /** cross-encoder 输入的父块候选数（聚合后取 top-N 送去重排） */
    private final int rerankerCandidates;
    /** 最终返回给 LLM 的父块数量 */
    private final int topParentResults;
    /** 向量得分低于此阈值的 child 直接丢弃 */
    private final double minChildScore;
    /** 联合分中向量权重（BM25 权重 = 1 - vectorWeight） */
    private final double vectorWeight;

    public ParentChildContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ParentChunkStore parentChunkStore,
            BM25ChildStore bm25ChildStore,
            CrossEncoderReranker crossEncoderReranker,
            int topChildResults,
            int rerankerCandidates,
            int topParentResults,
            double minChildScore,
            double vectorWeight) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.parentChunkStore = parentChunkStore;
        this.bm25ChildStore = bm25ChildStore;
        this.crossEncoderReranker = crossEncoderReranker;
        this.topChildResults = topChildResults;
        this.rerankerCandidates = rerankerCandidates;
        this.topParentResults = topParentResults;
        this.minChildScore = minChildScore;
        this.vectorWeight = vectorWeight;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String queryText = query.text();

        // ── Step 1: 并行召回 ────────────────────────────────────────────────────
        // 向量检索需要先调用远程 embedding API，是耗时瓶颈；
        // BM25 是本地 DB 查询，两者并行可节省约 50% 总耗时。
        CompletableFuture<List<EmbeddingMatch<TextSegment>>> vectorFuture =
                CompletableFuture.supplyAsync(() -> {
                    Embedding queryEmbedding = embeddingModel.embed(queryText).content();
                    EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(topChildResults)
                            .minScore(minChildScore)
                            .build();
                    return embeddingStore.search(req).matches();
                });

        CompletableFuture<List<BM25ChildStore.BM25Result>> bm25Future =
                CompletableFuture.supplyAsync(() ->
                        bm25ChildStore.search(queryText, topChildResults));

        CompletableFuture.allOf(vectorFuture, bm25Future).join();
        List<EmbeddingMatch<TextSegment>> vectorMatches = vectorFuture.join();
        List<BM25ChildStore.BM25Result>  bm25Matches   = bm25Future.join();

        log.info("[RAG] 召回层：向量命中 {} 个 child，BM25 命中 {} 个 child",
                vectorMatches.size(), bm25Matches.size());

        // ── Step 2: 按 parent_id 聚合，计算联合分 ──────────────────────────────
        Map<String, ParentAggregation> aggregation = new LinkedHashMap<>();

        for (EmbeddingMatch<TextSegment> m : vectorMatches) {
            String parentId = m.embedded().metadata().getString("parent_id");
            if (parentId == null) {
                log.warn("[RAG] 发现无 parent_id 的 child（text前缀: {}），跳过",
                        m.embedded().text().substring(0, Math.min(40, m.embedded().text().length())));
                continue;
            }
            aggregation.computeIfAbsent(parentId, k -> new ParentAggregation())
                    .addVectorHit(m.score(), m.embedded().text());
        }

        for (BM25ChildStore.BM25Result r : bm25Matches) {
            aggregation.computeIfAbsent(r.parentId(), k -> new ParentAggregation())
                    .addBm25Hit(r.score(), r.childContent());
        }

        log.info("[RAG] 聚合层：共 {} 个不同 parent", aggregation.size());

        // ── Step 3: 按联合分降序，取 top-N 候选送 cross-encoder ───────────────
        int candidateCount = Math.min(aggregation.size(), rerankerCandidates);
        List<Map.Entry<String, ParentAggregation>> candidates = aggregation.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().combinedScore(vectorWeight),
                                                  a.getValue().combinedScore(vectorWeight)))
                .limit(candidateCount)
                .collect(Collectors.toList());

        // 准备 cross-encoder 输入：用每个 parent 下最优 child 文本作为代表
        // （child ≈ 400 chars，符合 cross-encoder 输入长度限制；parent 原文作为最终上下文）
        List<String> representativeTexts = candidates.stream()
                .map(e -> e.getValue().bestChildText())
                .collect(Collectors.toList());

        List<String> candidateParentIds = candidates.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // ── Step 4: Cross-encoder 重排 ─────────────────────────────────────────
        List<CrossEncoderReranker.RerankResult> reranked =
                crossEncoderReranker.rerank(queryText, representativeTexts);

        // ── Step 5: 输出层 —— 按重排分取 top-N parent 原文 ────────────────────
        List<Content> contents = new ArrayList<>();
        for (CrossEncoderReranker.RerankResult r : reranked) {
            if (contents.size() >= topParentResults) break;
            if (r.index() >= candidateParentIds.size()) continue;

            String parentId = candidateParentIds.get(r.index());
            String parentContent = parentChunkStore.getById(parentId);
            if (parentContent == null) {
                log.warn("[RAG] parent_id={} 在 DB 中未找到，跳过", parentId);
                continue;
            }
            log.debug("[RAG] 选中 parent_id={} | cross-encoder score={:.4f}",
                    parentId, r.score());
            contents.add(Content.from(TextSegment.from(parentContent)));
        }

        log.info("[RAG] 输出层：返回 {} 个 parent 作为 RAG 上下文", contents.size());
        return contents;
    }

    // ── 内部聚合类 ─────────────────────────────────────────────────────────────

    private static class ParentAggregation {

        double maxVectorScore = 0.0;
        double maxBm25Score   = 0.0;
        /** 优先使用向量最优 child 文本（语义质量更高），无向量命中时退回 BM25 文本 */
        String vectorBestText = null;
        String bm25BestText   = null;

        void addVectorHit(double score, String text) {
            if (score > maxVectorScore) {
                maxVectorScore = score;
                vectorBestText = text;
            }
        }

        void addBm25Hit(double score, String text) {
            if (score > maxBm25Score) {
                maxBm25Score = score;
                bm25BestText = text;
            }
        }

        /** 联合分 = vectorWeight × maxVectorScore + (1-vectorWeight) × maxBm25Score */
        double combinedScore(double vectorWeight) {
            return vectorWeight * maxVectorScore + (1 - vectorWeight) * maxBm25Score;
        }

        String bestChildText() {
            if (vectorBestText != null) return vectorBestText;
            return bm25BestText != null ? bm25BestText : "";
        }
    }
}
