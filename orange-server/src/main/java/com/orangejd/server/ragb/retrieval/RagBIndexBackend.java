package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;

import java.util.List;
import java.util.Map;

public interface RagBIndexBackend {
    String adapterType();

    Map<Long, String> index(RagBDocument document, RagBIndexGeneration generation, List<RagBChunk> chunks);

    RagBVectorSearchResult searchWithTrace(RagBIndexGeneration generation, String query, int topK,
                                           double threshold, RagBSearchConstraints constraints);
}
