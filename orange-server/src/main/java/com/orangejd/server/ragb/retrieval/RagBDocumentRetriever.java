package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.ragb.service.RagBSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Spring AI RAG SPI adapter, matching the component boundary used by Spring AI Alibaba examples. */
@Component
public class RagBDocumentRetriever implements DocumentRetriever {
    public static final String TOP_K = "rag_b_top_k";
    public static final String SIMILARITY_THRESHOLD = "rag_b_similarity_threshold";
    public static final String SOURCE_TYPES = "rag_b_source_types";

    private final RagBSearchService searchService;

    public RagBDocumentRetriever(RagBSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public List<Document> retrieve(Query query) {
        int topK = integer(query.context().get(TOP_K), 8);
        double threshold = decimal(query.context().get(SIMILARITY_THRESHOLD), 0.55);
        Set<String> sources = sources(query.context().get(SOURCE_TYPES));
        RagBSearchService.SearchResult result = searchService.retrieve(query.text(), topK, threshold, sources);
        if (!"ANSWER_WITH_CITATIONS".equals(result.answerPolicy())) return List.of();
        return result.hits().stream()
                .map(this::document).toList();
    }

    private Document document(RagBSearchHit hit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", hit.documentId());
        metadata.put("versionId", hit.versionId());
        metadata.put("chunkId", hit.chunkId());
        metadata.put("chunkIndex", hit.chunkIndex());
        metadata.put("title", hit.title());
        metadata.put("sourceType", hit.sourceType());
        metadata.put("sourceRef", hit.sourceRef());
        metadata.put("sectionPath", hit.sectionPath() == null ? "" : hit.sectionPath());
        metadata.put("startOffset", hit.startOffset());
        metadata.put("endOffset", hit.endOffset());
        metadata.put("score", hit.score());
        if(hit.rerankScore()!=null)metadata.put("rerankScore",hit.rerankScore());
        metadata.put("retrievalRoute", hit.retrievalRoute());
        return new Document("rag-b-" + hit.chunkId(), hit.content(), metadata);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private Set<String> sources(Object value) {
        if (value instanceof Set<?> set) return set.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        if (value instanceof List<?> list) return list.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        return Set.of();
    }
}
