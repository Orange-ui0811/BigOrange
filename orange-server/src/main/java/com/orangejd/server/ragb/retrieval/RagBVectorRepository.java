package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class RagBVectorRepository implements RagBIndexBackend {
    private final RagBEmbeddingModels embeddingModels;
    private EmbeddingModel model(RagBIndexGeneration generation) {
        return embeddingModels == null ? embeddingModel : embeddingModels.forGeneration(generation);
    }
    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RagBProperties properties;
    private final RagBConstraintMatcher constraintsMatcher;

    public RagBVectorRepository(EmbeddingModel embeddingModel, StringRedisTemplate redis,
                                ObjectMapper objectMapper, RagBProperties properties,
                                RagBConstraintMatcher constraintsMatcher) {
        this(embeddingModel,redis,objectMapper,properties,constraintsMatcher,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RagBVectorRepository(EmbeddingModel embeddingModel, StringRedisTemplate redis,
                                ObjectMapper objectMapper, RagBProperties properties,
                                RagBConstraintMatcher constraintsMatcher,RagBEmbeddingModels embeddingModels) {
        this.embeddingModels=embeddingModels;
        this.embeddingModel = embeddingModel;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.constraintsMatcher = constraintsMatcher;
    }

    @Override
    public Map<Long, String> index(RagBDocument document, RagBIndexGeneration generation,
                                   List<RagBChunk> chunks) {
        validateGeneration(generation);
        Map<Long, String> keys = new LinkedHashMap<>();
        int batchSize = properties.getEmbeddingBatchSize();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            List<RagBChunk> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            List<float[]> vectors = model(generation).embed(batch.stream().map(RagBChunk::getContent).toList());
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException("向量服务返回数量与请求数量不一致");
            }
            for (int i = 0; i < batch.size(); i++) {
                RagBChunk chunk = batch.get(i);
                float[] vector = vectors.get(i);
                if (vector.length == 0) throw new IllegalStateException("向量服务返回空向量");
                String key = generation.getRedisPrefix() + "chunk:" + chunk.getId();
                Map<String, String> value = new LinkedHashMap<>();
                value.put("documentId", String.valueOf(document.getId()));
                value.put("versionId", String.valueOf(chunk.getVersionId()));
                value.put("chunkId", String.valueOf(chunk.getId()));
                value.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
                value.put("title", document.getTitle());
                value.put("sourceType", document.getSourceType());
                value.put("sourceRef", document.getSourceRef());
                value.put("sectionPath", nullToEmpty(chunk.getSectionPath()));
                value.put("startOffset", String.valueOf(chunk.getStartOffset()));
                value.put("endOffset", String.valueOf(chunk.getEndOffset()));
                value.put("content", chunk.getContent());
                value.put("embedding", json(vector));
                copyMetadata(chunk, value);
                redis.opsForHash().putAll(key, value);
                redis.opsForSet().add(memberSet(generation), key);
                keys.put(chunk.getId(), key);
            }
        }
        return keys;
    }

    public List<RagBSearchHit> search(RagBIndexGeneration generation, String query, int topK,
                                      double threshold, Set<String> allowedSources) {
        return searchWithTrace(generation, query, topK, threshold,
                RagBSearchConstraints.sources(allowedSources)).hits();
    }

    @Override
    public RagBVectorSearchResult searchWithTrace(RagBIndexGeneration generation, String query, int topK,
                                                   double threshold, RagBSearchConstraints constraints) {
        validateGeneration(generation);
        float[] queryVector = model(generation).embed(query);
        if (queryVector.length == 0) throw new IllegalStateException("查询向量为空");
        Set<String> members = redis.opsForSet().members(memberSet(generation));
        if (members == null || members.isEmpty()) return new RagBVectorSearchResult(List.of(), 0, Map.of());
        if (members.size() > properties.getSearchCandidateLimit()) {
            throw new IllegalStateException("B 版基础检索候选超过安全上限，请启用 RediSearch 适配器");
        }
        List<RagBSearchHit> hits = new ArrayList<>();
        Map<String, Integer> filterReasons = new LinkedHashMap<>();
        for (String key : members) {
            Map<Object, Object> raw = redis.opsForHash().entries(key);
            if (raw.isEmpty()) continue;
            String sourceType = string(raw, "sourceType");
            String filterReason = constraintsMatcher.filterReason(raw, constraints);
            if (filterReason != null) {
                filterReasons.merge(filterReason, 1, Integer::sum);
                continue;
            }
            float[] vector = parseVector(string(raw, "embedding"));
            double score = cosine(queryVector, vector);
            if (score < threshold) continue;
            hits.add(new RagBSearchHit(
                    number(raw, "documentId"), number(raw, "versionId"), number(raw, "chunkId"),
                    (int) number(raw, "chunkIndex"), string(raw, "title"), sourceType,
                    string(raw, "sourceRef"), emptyToNull(string(raw, "sectionPath")),
                    (int) number(raw, "startOffset"), (int) number(raw, "endOffset"),
                    string(raw, "content"), score, "VECTOR_EXACT"));
        }
        List<RagBSearchHit> selected = hits.stream().sorted(Comparator.comparingDouble(RagBSearchHit::score).reversed())
                .limit(Math.max(1, Math.min(topK, 50))).toList();
        return new RagBVectorSearchResult(selected, members.size(), Map.copyOf(filterReasons));
    }

    @Override
    public String adapterType() { return "EXACT"; }

    public void delete(RagBIndexGeneration generation, List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        validateGeneration(generation);
        List<String> owned = keys.stream().filter(key -> key.startsWith(generation.getRedisPrefix())).toList();
        if (owned.size() != keys.size()) throw new IllegalArgumentException("拒绝删除 B 版命名空间之外的键");
        redis.delete(owned);
        redis.opsForSet().remove(memberSet(generation), owned.toArray());
    }

    public void deleteOwned(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        if (keys.stream().anyMatch(key -> key == null || !key.startsWith(properties.getRedisPrefix()) || key.contains("*"))) {
            throw new IllegalArgumentException("拒绝删除 B 版命名空间之外的键");
        }
        redis.delete(keys);
        Map<String, List<String>> byMemberSet = new LinkedHashMap<>();
        for (String key : keys) {
            int chunkMarker = key.lastIndexOf("chunk:");
            if (chunkMarker < properties.getRedisPrefix().length()) continue;
            byMemberSet.computeIfAbsent(key.substring(0, chunkMarker) + "members", ignored -> new ArrayList<>()).add(key);
        }
        byMemberSet.forEach((setKey, members) -> redis.opsForSet().remove(setKey, members.toArray()));
    }

    private void validateGeneration(RagBIndexGeneration generation) {
        if (generation == null || generation.getRedisPrefix() == null
                || !generation.getRedisPrefix().startsWith(properties.getRedisPrefix())
                || generation.getRedisPrefix().contains("*")) {
            throw new IllegalStateException("B 版索引代际或 Redis 命名空间无效");
        }
    }

    private String memberSet(RagBIndexGeneration generation) { return generation.getRedisPrefix() + "members"; }
    private void copyMetadata(RagBChunk chunk, Map<String, String> target) {
        if (chunk.getMetadataJson() == null || chunk.getMetadataJson().isBlank()) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = objectMapper.readValue(chunk.getMetadataJson(), Map.class);
            for (String key : List.of("language", "techStack", "difficulty", "duration", "durationMaxDays",
                    "requiresDocker", "requiresDatabase", "requiresSecrets", "requirementsKnown", "repositoryUrl",
                    "businessId")) {
                Object value = metadata.get(key);
                if (value != null) target.put(key, value.toString());
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("分块元数据损坏: " + chunk.getId(), ex);
        }
    }

    private String json(float[] vector) {
        try { return objectMapper.writeValueAsString(vector); }
        catch (JsonProcessingException e) { throw new IllegalStateException("向量序列化失败", e); }
    }
    private float[] parseVector(String value) {
        try { return objectMapper.readValue(value, float[].class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("向量数据损坏", e); }
    }
    private String string(Map<Object, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? "" : value.toString();
    }
    private long number(Map<Object, Object> raw, String key) { return Long.parseLong(string(raw, key)); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private double cosine(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) return -1;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? -1 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
