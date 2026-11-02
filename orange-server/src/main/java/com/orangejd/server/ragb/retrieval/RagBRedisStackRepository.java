package com.orangejd.server.ragb.retrieval;

import org.springframework.stereotype.Repository;
import static com.orangejd.server.ragb.retrieval.RagBQueryTokenizer.*;
import static com.orangejd.server.ragb.retrieval.RagBIndexFields.FIELDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import redis.clients.jedis.search.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RagBRedisStackRepository implements RagBIndexBackend {
    private final ObjectProvider<VectorStore> platformStore;
    private final EmbeddingModel embeddingModel;
    private final RagBEmbeddingModels embeddingModels;
    private final ObjectMapper objectMapper;
    private final RagBProperties properties;
    private final RagBConstraintMatcher constraintsMatcher;
    private final Map<Long, RedisVectorStore> stores = new ConcurrentHashMap<>();

    public RagBRedisStackRepository(ObjectProvider<VectorStore> platformStore, EmbeddingModel embeddingModel,
                                    ObjectMapper objectMapper, RagBProperties properties,
                                    RagBConstraintMatcher constraintsMatcher) {
        this(platformStore,embeddingModel,objectMapper,properties,constraintsMatcher,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RagBRedisStackRepository(ObjectProvider<VectorStore> platformStore, EmbeddingModel embeddingModel,
                                    ObjectMapper objectMapper, RagBProperties properties,
                                    RagBConstraintMatcher constraintsMatcher, RagBEmbeddingModels embeddingModels) {
        this.embeddingModels = embeddingModels;
        this.platformStore = platformStore;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.constraintsMatcher = constraintsMatcher;
    }

    @Override
    public String adapterType() { return "HNSW_HYBRID"; }

    @Override
    public Map<Long, String> index(RagBDocument source, RagBIndexGeneration generation, List<RagBChunk> chunks) {
        RedisVectorStore store = store(generation);
        Map<Long, String> keys = new LinkedHashMap<>();
        int batchSize = properties.getEmbeddingBatchSize();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            List<RagBChunk> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            List<Document> documents = batch.stream().map(chunk -> toDocument(source, chunk)).toList();
            store.add(documents);
            awaitSearchVisibility(store, generation, batch);
            for (RagBChunk chunk : batch) keys.put(chunk.getId(), generation.getRedisPrefix() + chunk.getId());
        }
        return keys;
    }

    private void awaitSearchVisibility(RedisVectorStore store, RagBIndexGeneration generation, List<RagBChunk> batch) {
        Set<Long> pending = new LinkedHashSet<>();
        batch.forEach(chunk -> pending.add(chunk.getId()));
        for (int attempt = 0; attempt < 40 && !pending.isEmpty(); attempt++) {
            pending.removeIf(id -> store.getJedis().ftSearch(indexName(generation),
                    new Query("@chunkId:[" + id + " " + id + "]").limit(0, 0).dialect(2)).getTotalResults() > 0);
            if (!pending.isEmpty()) {
                try { Thread.sleep(25); }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待 Redis 搜索索引时被中断", ex);
                }
            }
        }
        if (!pending.isEmpty()) throw new IllegalStateException("Redis 搜索索引可见性校验失败，缺少分块: " + pending);
    }

    @Override
    public RagBVectorSearchResult searchWithTrace(RagBIndexGeneration generation, String query, int topK,
                                                   double threshold, RagBSearchConstraints constraints) {
        RedisVectorStore store = com.orangejd.server.service.TrendLabObservation.measure("B.indexStore", () -> store(generation));
        int candidateLimit = properties.getRouteCandidateLimit();
        SearchRequest.Builder vectorRequest = SearchRequest.builder().query(query)
                .topK(candidateLimit).similarityThreshold(Math.max(0, threshold));
        String portableFilter = portablePreFilter(constraints);
        if (portableFilter != null) vectorRequest.filterExpression(portableFilter);
        List<Document> vector = com.orangejd.server.service.TrendLabObservation.measure("B.embeddingAndVector", () -> store.similaritySearch(vectorRequest.build()));
        List<Document> names = com.orangejd.server.service.TrendLabObservation.measure("B.names", () -> lexical(store, generation, query, candidateLimit, constraints,
                List.of("title", "sourceRef")));
        List<String> aliases = java.util.stream.Stream.concat(vector.stream(), names.stream())
                .flatMap(d -> java.util.stream.Stream.of(string(d.getMetadata(), "title"), string(d.getMetadata(), "sourceRef")))
                .distinct().toList();
        List<String> contentTerms = RagBContentQuery.terms(query, aliases);
        List<Document> lexical = com.orangejd.server.service.TrendLabObservation.measure("B.lexical", () -> lexical(store, generation, query, candidateLimit, constraints,
                List.of("content"), contentTerms));

        long fusionStarted=System.nanoTime();
        Map<String, Ranked> merged = new LinkedHashMap<>();
        addRanks(merged, vector, RankChannel.VECTOR);
        addRanks(merged, lexical, RankChannel.LEXICAL);
        addRanks(merged, names, RankChannel.NAME);
        List<Ranked> ranked = merged.values().stream()
                .filter(value -> score(value, query) > 0)
                .sorted(Comparator.comparingDouble((Ranked value) -> score(value, query)).reversed()).toList();
        Map<String, Integer> filterReasons = new LinkedHashMap<>();
        List<RagBSearchHit> hits = new ArrayList<>();
        for (Ranked candidate : ranked) {
            String reason = constraintsMatcher.filterReason(candidate.document().getMetadata(), constraints);
            if (reason != null) {
                filterReasons.merge(reason, 1, Integer::sum);
                continue;
            }
            hits.add(toHit(candidate, query));
            if (hits.size() >= Math.max(1, Math.min(topK, 200))) break;
        }
        com.orangejd.server.service.TrendLabObservation.elapsed("B.nativeFusion",fusionStarted);
        return new RagBVectorSearchResult(List.copyOf(hits), merged.size(), Map.copyOf(filterReasons));
    }

    private RedisVectorStore store(RagBIndexGeneration generation) {
        validate(generation);
        return stores.computeIfAbsent(generation.getId(), ignored -> {
            VectorStore existing = platformStore.getIfAvailable();
            if (!(existing instanceof RedisVectorStore redis)) {
                throw new IllegalStateException("HNSW 混合检索需要平台 Redis Stack VectorStore");
            }
            EmbeddingModel model = embeddingModels == null ? embeddingModel : embeddingModels.forGeneration(generation);
            RedisVectorStore created = RedisVectorStore.builder(redis.getJedis(), model)
                    .indexName(indexName(generation)).prefix(generation.getRedisPrefix())
                    .vectorAlgorithm(RedisVectorStore.Algorithm.HSNW)
                    .metadataFields(FIELDS).initializeSchema(true).build();
            created.afterPropertiesSet();
            return created;
        });
    }

    private Document toDocument(RagBDocument source, RagBChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", source.getId());
        metadata.put("versionId", chunk.getVersionId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("title", source.getTitle());
        metadata.put("sourceType", source.getSourceType());
        metadata.put("sourceRef", source.getSourceRef());
        metadata.put("sectionPath", value(chunk.getSectionPath()));
        metadata.put("startOffset", chunk.getStartOffset());
        metadata.put("endOffset", chunk.getEndOffset());
        metadata.putAll(metadata(chunk));
        return Document.builder().id(String.valueOf(chunk.getId())).text(chunk.getContent()).metadata(metadata).build();
    }

    private Map<String, Object> metadata(RagBChunk chunk) {
        if (chunk.getMetadataJson() == null || chunk.getMetadataJson().isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked") Map<String, Object> parsed = objectMapper.readValue(chunk.getMetadataJson(), Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            for (RedisVectorStore.MetadataField field : FIELDS) {
                Object current = parsed.get(field.name());
                if (current != null) result.put(field.name(), current);
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("分块元数据损坏: " + chunk.getId(), ex);
        }
    }

    private List<Document> lexical(RedisVectorStore store, RagBIndexGeneration generation,
                                   String input, int limit, RagBSearchConstraints constraints,
                                   List<String> fields) {
        return lexical(store, generation, input, limit, constraints, fields, tokenize(input));
    }

    private List<Document> lexical(RedisVectorStore store, RagBIndexGeneration generation,
                                   String input, int limit, RagBSearchConstraints constraints,
                                   List<String> fields, List<String> terms) {
        if (terms.isEmpty()) return List.of();
        String sourceFilter = redisSourceFilter(constraints);
        String alternatives = terms.stream().map(RagBQueryTokenizer::escapeQueryTerm)
                .collect(java.util.stream.Collectors.joining(" | "));
        String fieldExpression = fields.stream().map(field -> "@" + field + ":(" + alternatives + ")")
                .collect(java.util.stream.Collectors.joining(" | "));
        String expression = sourceFilter + "(" + fieldExpression + ")";
        List<String> returnFields = new ArrayList<>();
        returnFields.add("content");
        FIELDS.forEach(field -> returnFields.add(field.name()));
        var result = store.getJedis().ftSearch(indexName(generation), new Query(expression)
                .returnFields(returnFields.toArray(String[]::new)).limit(0, limit).dialect(2));
        return result.getDocuments().stream().map(hit -> {
            Map<String, Object> metadata = new LinkedHashMap<>();
            FIELDS.stream().map(RedisVectorStore.MetadataField::name).filter(hit::hasProperty)
                    .forEach(name -> metadata.put(name, hit.getString(name)));
            String id = hit.getId().substring(generation.getRedisPrefix().length());
            return Document.builder().id(id).text(hit.getString("content")).metadata(metadata).build();
        }).toList();
    }

    private String portablePreFilter(RagBSearchConstraints constraints) {
        if(constraints==null) return null;
        List<String> clauses=new ArrayList<>();
        if(!constraints.sourceTypes().isEmpty()) clauses.add("sourceType in ["+constraints.sourceTypes().stream().sorted().map(value->"'"+value.replace("'","")+"'").collect(java.util.stream.Collectors.joining(","))+"]");
        if(!constraints.documentIds().isEmpty()) clauses.add("("+constraints.documentIds().stream().sorted().map(id->"documentId == "+id).collect(java.util.stream.Collectors.joining(" || "))+")");
        return clauses.isEmpty()?null:String.join(" && ",clauses);
    }

    private String redisSourceFilter(RagBSearchConstraints constraints) {
        if (constraints == null) return "";
        String tags = constraints.sourceTypes().stream().sorted()
                .map(value -> value.replaceAll("[^A-Za-z0-9_-]", ""))
                .collect(java.util.stream.Collectors.joining("|"));
        String documents=constraints.documentIds().isEmpty()?"":"("+constraints.documentIds().stream().sorted().map(id->"@documentId:["+id+" "+id+"]").collect(java.util.stream.Collectors.joining(" | "))+") ";
        return (tags.isBlank() ? "" : "@sourceType:{" + tags + "} ")+documents;
    }

    private void addRanks(Map<String, Ranked> merged, List<Document> documents, RankChannel channel) {
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            Ranked previous = merged.get(document.getId());
            int vectorRank = previous == null ? 0 : previous.vectorRank();
            int lexicalRank = previous == null ? 0 : previous.lexicalRank();
            int nameRank = previous == null ? 0 : previous.nameRank();
            if (channel == RankChannel.VECTOR) vectorRank = index + 1;
            else if (channel == RankChannel.LEXICAL) lexicalRank = index + 1;
            else nameRank = index + 1;
            Document best = previous == null || channel == RankChannel.VECTOR ? document : previous.document();
            merged.put(document.getId(), new Ranked(best, vectorRank, lexicalRank, nameRank));
        }
    }

    private RagBSearchHit toHit(Ranked ranked, String query) {
        Map<String, Object> metadata = ranked.document().getMetadata();
        boolean exactName = RagBFusionScorer.exactNameMatch(query, string(metadata, "title"), string(metadata, "sourceRef"));
        String route = "HYBRID_RRF(vectorRank=" + ranked.vectorRank() + ",lexicalRank=" + ranked.lexicalRank()
                + ",nameRank=" + ranked.nameRank() + ",lexicalWeight=" + RagBFusionScorer.LEXICAL_WEIGHT
                + ",nameGated=true,rankPolicy=" + RagBProjectEvidenceScorer.POLICY
                + ",contentQueryPolicy=" + RagBContentQuery.POLICY + ")" + (exactName ? "+NAME_EXACT" : "");
        return new RagBSearchHit(number(metadata, "documentId"), number(metadata, "versionId"),
                number(metadata, "chunkId"), (int) number(metadata, "chunkIndex"), string(metadata, "title"),
                string(metadata, "sourceType"), string(metadata, "sourceRef"), nullable(metadata, "sectionPath"),
                (int) number(metadata, "startOffset"), (int) number(metadata, "endOffset"),
                ranked.document().getText(), score(ranked, query), route);
    }

    private double score(Ranked ranked, String query) {
        Map<String, Object> metadata = ranked.document().getMetadata();
        return RagBProjectEvidenceScorer.score(query, string(metadata, "title"), string(metadata, "sourceRef"),
                ranked.vectorRank(), ranked.lexicalRank());
    }

    private String indexName(RagBIndexGeneration generation) {
        return "orange-rag-b-" + generation.getGenerationKey().replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private void validate(RagBIndexGeneration generation) {
        if (generation == null || generation.getId() == null || generation.getRedisPrefix() == null
                || !generation.getRedisPrefix().startsWith(properties.getRedisPrefix())
                || generation.getRedisPrefix().contains("*")) {
            throw new IllegalStateException("B 版 HNSW 索引代际无效");
        }
    }

    private String string(Map<String, Object> map, String key) {
        Object current = map.get(key);
        return current == null ? "" : current.toString();
    }
    private long number(Map<String, Object> map, String key) { return Math.round(Double.parseDouble(string(map, key))); }
    private String nullable(Map<String, Object> map, String key) { String current = string(map, key); return current.isBlank() ? null : current; }
    private String value(String current) { return current == null ? "" : current; }

    private enum RankChannel { VECTOR, LEXICAL, NAME }

    private record Ranked(Document document, int vectorRank, int lexicalRank, int nameRank) {}
}
