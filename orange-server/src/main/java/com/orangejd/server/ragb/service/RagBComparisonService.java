package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBExperimentRun;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.service.TrendRedisMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagBComparisonService {
    private final RagBProperties properties;
    private final RagBMapper mapper;
    private final RagBSearchService bSearch;
    private final VectorStore aStore;
    private final AiEmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final String aPrefix;

    public RagBComparisonService(RagBProperties properties, RagBMapper mapper, RagBSearchService bSearch,
                                 ObjectProvider<VectorStore> aStore, AiEmbeddingProperties embeddingProperties,
                                 ObjectMapper objectMapper,
                                 ObjectProvider<RedisVectorStoreProperties> redisProperties) {
        this.properties = properties;
        this.mapper = mapper;
        this.bSearch = bSearch;
        this.aStore = aStore.getIfAvailable();
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
        RedisVectorStoreProperties redis = redisProperties.getIfAvailable(RedisVectorStoreProperties::new);
        this.aPrefix = redis.getPrefix();
    }

    public Map<String, Object> compareRetrieval(String query, int requestedTopK, double threshold) {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("检索问题不能为空");
        int topK = Math.max(1, Math.min(requestedTopK, 20));
        RagBExperimentRun run = new RagBExperimentRun();
        run.setRunKey("retrieval-" + UUID.randomUUID());
        run.setMode("RETRIEVAL_AB");
        run.setDatasetVersion("live-a__rag-b-active");
        run.setModelSnapshot(json(Map.of("embeddingModel", embeddingProperties.getModel(),
                "embeddingEndpoint", embeddingProperties.getEndpoint())));
        run.setRetrievalSnapshot(json(Map.of("topK", topK, "threshold", threshold,
                "aAdapter", "SPRING_AI_VECTOR_STORE", "bAdapter", "ACTIVE_GENERATION")));
        run.setStatus("RUNNING");
        mapper.insertExperiment(run);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runKey", run.getRunKey());
        result.put("query", query);
        String aError = null;
        String bError = null;
        long started = System.nanoTime();
        try {
            if (aStore == null) throw new IllegalStateException("A 版 VectorStore 不可用");
            List<Document> documents = aStore.similaritySearch(SearchRequest.builder().query(query)
                    .topK(topK).similarityThreshold(threshold).build());
            documents = TrendRedisMetadata.restore(aStore, aPrefix, documents);
            result.put("a", Map.of("durationMs", elapsedMillis(started), "hits", documents.stream().map(this::aHit).toList()));
        } catch (RuntimeException ex) {
            aError = message(ex);
            result.put("a", Map.of("durationMs", elapsedMillis(started), "error", aError, "hits", List.of()));
        }
        started = System.nanoTime();
        try {
            RagBSearchService.SearchResult b = bSearch.retrieve(query, topK, threshold, java.util.Set.of());
            result.put("b", Map.of("durationMs", elapsedMillis(started), "generation", b.generation(),
                    "adapter", b.adapter(), "answerPolicy", b.answerPolicy(),
                    "scannedCount", b.scannedCount(), "candidateCount", b.candidateCount(),
                    "excludedReasons", b.excludedReasons(), "hits", b.hits(), "evidenceGroups", b.evidenceGroups()));
        } catch (RuntimeException ex) {
            bError = message(ex);
            result.put("b", Map.of("durationMs", elapsedMillis(started), "error", bError, "hits", List.of()));
        }
        run.setStatus(aError == null && bError == null ? "SUCCEEDED" : aError != null && bError != null ? "FAILED" : "PARTIAL_FAILED");
        run.setResultJson(json(result));
        run.setLastError(aError == null ? bError : bError == null ? aError : "A: " + aError + "; B: " + bError);
        mapper.completeExperiment(run);
        result.put("status", run.getStatus());
        return result;
    }

    public List<RagBExperimentRun> list(int limit) {
        return mapper.selectExperiments(Math.max(1, Math.min(limit, 100)));
    }

    private Map<String, Object> aHit(Document document) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", document.getId());
        hit.put("score", document.getScore());
        hit.put("content", document.getText());
        hit.put("metadata", document.getMetadata());
        return hit;
    }

    private long elapsedMillis(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }
    private String message(RuntimeException ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("实验记录序列化失败", ex); }
    }
}
