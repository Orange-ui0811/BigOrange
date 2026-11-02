package com.orangejd.server.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.json.Path;
import java.util.*;

/** Read compatibility for Redis JSON indexes without metadata aliases; no schema writes. */
public final class TrendRedisMetadata {
    static List<Document> lexical(VectorStore store, String prefix, String query) {
        if (!(store instanceof RedisVectorStore redis) || query.isBlank()) return List.of();
        var request = new redis.clients.jedis.search.Query("@content:(" + query + ")")
                .returnFields("content").limit(0, 12);
        List<Document> docs = redis.getJedis().ftSearch(
                redis.createObservationContextBuilder("query").build().getCollectionName(), request)
                .getDocuments().stream().map(hit -> Document.builder()
                        .id(hit.getId().substring(prefix.length())).text(hit.getString("content"))
                        .metadata(Map.of("retrievalChannel", "LEXICAL")).build()).toList();
        return restore(store, prefix, docs);
    }
    private static final List<String> FIELDS = List.of("docId", "docTitle", "chunkIndex", "source",
            "sourceRef", "projectId", "projectType", "mappingStatus", "mappingMethod", "externalRepo", "catalog", "repositoryUrl");
    public static List<Document> restore(VectorStore store, String prefix, List<Document> documents) {
        if (!(store instanceof RedisVectorStore redis) || documents.isEmpty()) return documents;
        String[] keys = documents.stream().map(d -> prefix + d.getId()).toArray(String[]::new);
        List<Map> originals = redis.getJedis().jsonMGet(Path.ROOT_PATH, Map.class, keys);
        if (originals.size() != documents.size()) throw new IllegalStateException("Incomplete Redis metadata response");
        List<Document> restored = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) restored.add(merge(documents.get(i), originals.get(i)));
        return restored;
    }
    static Document merge(Document document, Map<?, ?> original) {
        if (original == null) return document;
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        for (String field : FIELDS) {
            Object value = original.get(field);
            if (value instanceof String || value instanceof Number || value instanceof Boolean)
                metadata.put(field, field.equals("chunkIndex") && value instanceof Number n ? n.intValue() : value);
        }
        return Document.builder().id(document.getId()).text(document.getText())
                .metadata(metadata).score(document.getScore()).build();
    }
}
