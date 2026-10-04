package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.ingest.RagBBusinessMetadataProvider;
import com.orangejd.server.ragb.mapper.RagBMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RagBRetrievalMetadataResolver {
    private final RagBMapper mapper;
    private final ObjectMapper objectMapper;
    private final RagBBusinessMetadataProvider businessMetadata;

    public RagBRetrievalMetadataResolver(RagBMapper mapper, ObjectMapper objectMapper,
                                         RagBBusinessMetadataProvider businessMetadata) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.businessMetadata = businessMetadata;
    }

    public Map<String, Object> resolve(RagBDocument document, RagBSearchHit hit) {
        Map<String, Object> result = new LinkedHashMap<>();
        RagBChunk chunk = mapper.selectChunk(hit.chunkId());
        if (chunk != null && chunk.getMetadataJson() != null && !chunk.getMetadataJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked") Map<String, Object> parsed = objectMapper.readValue(chunk.getMetadataJson(), Map.class);
                result.putAll(parsed);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("分块元数据损坏: " + hit.chunkId(), ex);
            }
        }
        result.put("sourceType", hit.sourceType());
        result.put("documentId",hit.documentId());
        if (hit.sourceRef() != null) result.put("sourceRef", hit.sourceRef());
        result.putAll(businessMetadata.metadata(document));
        return Map.copyOf(result);
    }
}
