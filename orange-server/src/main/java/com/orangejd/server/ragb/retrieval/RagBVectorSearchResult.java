package com.orangejd.server.ragb.retrieval;

import java.util.List;
import java.util.Map;

public record RagBVectorSearchResult(List<RagBSearchHit> hits, int scannedCount,
                                     Map<String, Integer> filterReasons) {
}
