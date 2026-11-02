package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.service.TrendRedisMetadata;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBExperimentRun;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RagBEvaluationService {
    private final RagBProperties properties;
    private final RagBSearchService bSearch;
    private final VectorStore aStore;
    private final RagBMapper mapper;
    private final ObjectMapper objectMapper;
    private final AiEmbeddingProperties embedding;
    private final String aPrefix;
    private final RagBConstraintMatcher constraintMatcher;

    public RagBEvaluationService(RagBProperties properties, RagBSearchService bSearch,
                                 ObjectProvider<VectorStore> aStore, RagBMapper mapper,
                                 ObjectMapper objectMapper, AiEmbeddingProperties embedding,
                                 ObjectProvider<RedisVectorStoreProperties> redisProperties,
                                 RagBConstraintMatcher constraintMatcher) {
        this.properties = properties;
        this.bSearch = bSearch;
        this.aStore = aStore.getIfAvailable();
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.embedding = embedding;
        RedisVectorStoreProperties redis = redisProperties.getIfAvailable(RedisVectorStoreProperties::new);
        this.aPrefix = redis.getPrefix();
        this.constraintMatcher = constraintMatcher;
    }

    public Dataset dataset() {
        return readDataset("rag-b/evaluation-dataset.json");
    }

    public Dataset benchmarkDataset() {
        return readDataset("rag-b/evaluation-benchmark-v1.json");
    }

    private Dataset readDataset(String resource) {
        try { return objectMapper.readValue(new ClassPathResource(resource).getInputStream(), Dataset.class); }
        catch (IOException ex) { throw new IllegalStateException("固定评测集无法读取", ex); }
    }

    public Map<String, Object> run(int requestedTopK, double threshold) {
        return run(dataset(), requestedTopK, threshold);
    }

    public Map<String, Object> runBenchmark(int requestedTopK, double threshold) {
        return run(benchmarkDataset(), requestedTopK, threshold);
    }

    private Map<String, Object> run(Dataset dataset, int requestedTopK, double threshold) {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
        int topK = Math.max(1, Math.min(requestedTopK, 20));
        RagBExperimentRun run = begin(dataset, topK, threshold);
        List<Map<String, Object>> cases = new ArrayList<>();
        List<Long> aLatencies = new ArrayList<>();
        List<Long> bLatencies = new ArrayList<>();
        int aPassed = 0;
        int bPassed = 0;
        int traceable = 0;
        int citationReady = 0;
        int answerExpected = 0;
        int refusalExpected = 0;
        int bRefusalPassed = 0;
        int sourceGoldCases = 0;
        int aSourceGoldHits = 0;
        int bSourceGoldHits = 0;
        try {
            for (EvaluationCase value : dataset.cases()) {
                boolean refusalCase = expectsRefusal(value) || value.expectedSourceTypes().isEmpty();
                if (refusalCase) refusalExpected++; else answerExpected++;
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("key", value.key());
                current.put("question", value.question());
                current.put("expectedSourceTypes", value.expectedSourceTypes());
                current.put("expectedSourceRefs", value.expectedSourceRefs());
                if (!value.expectedSourceRefs().isEmpty()) sourceGoldCases++;
                long started = System.nanoTime();
                List<Document> aHits = List.of();
                String aError = null;
                try {
                    if (aStore == null) throw new IllegalStateException("A 版 VectorStore 不可用");
                    aHits = aStore.similaritySearch(SearchRequest.builder().query(value.question())
                            .topK(topK).similarityThreshold(threshold).build());
                    aHits = TrendRedisMetadata.restore(aStore, aPrefix, aHits);
                } catch (RuntimeException ex) { aError = message(ex); }
                long aMs = elapsed(started);
                aLatencies.add(aMs);
                boolean aPass = expectedA(value, aHits);
                if (!value.expectedSourceRefs().isEmpty() && matchesExpectedRef(value,
                        aHits.stream().map(this::sourceRef).filter(s -> !s.isBlank()).toList())) aSourceGoldHits++;
                if (aPass) aPassed++;
                current.put("a", side(aMs, aHits.size(), aPass, aError));

                started = System.nanoTime();
                RagBSearchService.SearchResult b = bSearch.retrieve(value.question(), topK, threshold, value.constraints());
                long bMs = elapsed(started);
                bLatencies.add(bMs);
                boolean bPass = expectedB(value, b);
                if (!value.expectedSourceRefs().isEmpty() && matchesExpectedRef(value,
                        b.hits().stream().map(hit -> hit.sourceRef()).toList())) bSourceGoldHits++;
                if (bPass) bPassed++;
                if (refusalCase && bPass) bRefusalPassed++;
                boolean caseTraceable = !b.hits().isEmpty() && b.hits().stream().allMatch(hit -> hit.chunkId() > 0
                        && hit.startOffset() >= 0 && hit.endOffset() > hit.startOffset());
                if (!refusalCase && caseTraceable) traceable++;
                if (!refusalCase && "ANSWER_WITH_CITATIONS".equals(b.answerPolicy())) citationReady++;
                current.put("b", Map.of("durationMs", bMs, "hitCount", b.hits().size(), "passed", bPass,
                        "adapter", b.adapter(), "answerPolicy", b.answerPolicy(), "traceable", caseTraceable,
                        "excludedReasons", b.excludedReasons()));
                cases.add(current);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("caseCount", dataset.cases().size());
            summary.put("aPassRate", rate(aPassed, dataset.cases().size()));
            summary.put("bPassRate", rate(bPassed, dataset.cases().size()));
            summary.put("answerExpectedCount", answerExpected);
            summary.put("refusalExpectedCount", refusalExpected);
            summary.put("bTraceabilityRate", rate(traceable, answerExpected));
            summary.put("bCitationReadyRate", rate(citationReady, answerExpected));
            summary.put("bRefusalAccuracy", rate(bRefusalPassed, refusalExpected));
            summary.put("sourceGoldCaseCount", sourceGoldCases);
            summary.put("aSourceRecallAtK", rate(aSourceGoldHits, sourceGoldCases));
            summary.put("bSourceRecallAtK", rate(bSourceGoldHits, sourceGoldCases));
            summary.put("aP50Ms", percentile(aLatencies, 0.50));
            summary.put("aP95Ms", percentile(aLatencies, 0.95));
            summary.put("bP50Ms", percentile(bLatencies, 0.50));
            summary.put("bP95Ms", percentile(bLatencies, 0.95));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runKey", run.getRunKey());
            result.put("datasetVersion", dataset.version());
            result.put("summary", summary);
            result.put("cases", cases);
            complete(run, "SUCCEEDED", result, null);
            return result;
        } catch (RuntimeException ex) {
            complete(run, "FAILED", Map.of("cases", cases), message(ex));
            throw ex;
        }
    }

    private RagBExperimentRun begin(Dataset dataset, int topK, double threshold) {
        RagBExperimentRun run = new RagBExperimentRun();
        run.setRunKey("fixed-eval-" + UUID.randomUUID());
        run.setMode("FIXED_EVAL");
        run.setDatasetVersion(dataset.version());
        run.setModelSnapshot(json(Map.of("embeddingModel", embedding.getModel(), "embeddingEndpoint", embedding.getEndpoint())));
        run.setRetrievalSnapshot(json(Map.of("topK", topK, "threshold", threshold, "caseCount", dataset.cases().size())));
        run.setStatus("RUNNING");
        mapper.insertExperiment(run);
        return run;
    }

    private void complete(RagBExperimentRun run, String status, Object result, String error) {
        run.setStatus(status);
        run.setResultJson(json(result));
        run.setLastError(error);
        mapper.completeExperiment(run);
    }

    private boolean expectedA(EvaluationCase value, List<Document> hits) {
        if (expectsRefusal(value)) return hits.isEmpty();
        if (value.expectedSourceTypes().isEmpty()) return hits.isEmpty();
        return hits.stream().anyMatch(hit -> {
            Map<String, Object> metadata = new LinkedHashMap<>(hit.getMetadata());
            metadata.put("sourceType", sourceType(hit));
            if (!metadata.containsKey("language") && metadata.containsKey("primaryLanguage")) {
                metadata.put("language", metadata.get("primaryLanguage"));
            }
            return constraintMatcher.filterReason(metadata, value.constraints()) == null;
        }) && (value.expectedSourceRefs().isEmpty() || matchesExpectedRef(value,
                hits.stream().map(this::sourceRef).filter(s -> !s.isBlank()).toList()));
    }

    private boolean expectedB(EvaluationCase value, RagBSearchService.SearchResult result) {
        String expectedPolicy = value.expectedAnswerPolicy();
        if (expectedPolicy == null || expectedPolicy.isBlank()) {
            expectedPolicy = value.expectedSourceTypes().isEmpty() ? "REFUSE_NO_MATCH" : "ANSWER_WITH_CITATIONS";
        }
        if (!expectedPolicy.equals(result.answerPolicy())) return false;
        if (expectedPolicy.startsWith("REFUSE")) return result.hits().isEmpty();
        if (value.expectedSourceTypes().isEmpty()) return result.hits().isEmpty();
        boolean sourceTypeMatches = result.hits().stream().anyMatch(hit -> value.expectedSourceTypes().contains(hit.sourceType()));
        return sourceTypeMatches && (value.expectedSourceRefs().isEmpty() || matchesExpectedRef(value,
                result.hits().stream().map(hit -> hit.sourceRef()).toList()));
    }
    private boolean expectsRefusal(EvaluationCase value) {
        return value.expectedAnswerPolicy() != null && value.expectedAnswerPolicy().startsWith("REFUSE");
    }
    private String sourceType(Document document) {
        Object value = document.getMetadata().get("sourceType");
        if (value == null) value = document.getMetadata().get("source");
        return value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
    }
    private String sourceRef(Document document) {
        for (String key : List.of("sourceRef", "source_ref", "repo", "repository", "repositoryUrl")) {
            Object value = document.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return "";
    }
    private boolean matchesExpectedRef(EvaluationCase value, List<String> actual) {
        return actual.stream().filter(java.util.Objects::nonNull).anyMatch(value.expectedSourceRefs()::contains);
    }
    private Map<String, Object> side(long duration, int count, boolean passed, String error) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("durationMs", duration);
        value.put("hitCount", count);
        value.put("passed", passed);
        if (error != null) value.put("error", error);
        return value;
    }
    private double rate(int numerator, int denominator) { return denominator == 0 ? 0 : (double) numerator / denominator; }
    private long elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }
    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
    private String message(RuntimeException ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("评测结果序列化失败", ex); }
    }

    public record Dataset(String version, List<EvaluationCase> cases) {
        public Dataset { cases = cases == null ? List.of() : List.copyOf(cases); }
    }
    public record EvaluationCase(String key, String question, Set<String> expectedSourceTypes,
                                 Set<String> requiredLanguages, Integer maxDurationDays, String difficulty,
                                 Boolean allowDocker, Boolean allowDatabase, Boolean allowSecrets,
                                 boolean beginnerPreferred, boolean careerPreferred,
                                 String expectedAnswerPolicy, Set<String> expectedSourceRefs) {
        public EvaluationCase {
            expectedSourceTypes = expectedSourceTypes == null ? Set.of() : Set.copyOf(expectedSourceTypes);
            requiredLanguages = requiredLanguages == null ? Set.of() : Set.copyOf(requiredLanguages);
            expectedSourceRefs = expectedSourceRefs == null ? Set.of() : Set.copyOf(expectedSourceRefs);
        }
        RagBSearchConstraints constraints() {
            return new RagBSearchConstraints(expectedSourceTypes, requiredLanguages, maxDurationDays, difficulty,
                    allowDocker, allowDatabase, allowSecrets, beginnerPreferred, careerPreferred);
        }
    }
}
