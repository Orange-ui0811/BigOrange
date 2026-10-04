package com.orangejd.server.ragb.retrieval;

public record RagBSearchHit(
        long documentId,
        long versionId,
        long chunkId,
        int chunkIndex,
        String title,
        String sourceType,
        String sourceRef,
        String sectionPath,
        int startOffset,
        int endOffset,
        String content,
        double score,
        String retrievalRoute) {
}
