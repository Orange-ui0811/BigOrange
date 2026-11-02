package com.orangejd.server.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkerTest {
    @Test
    void doesNotLoseCharactersBetweenAChunkAndItsOverlap() {
        String marker = "BOUNDARY_MARKER_425";
        String content = "a".repeat(425) + marker + "b".repeat(700);

        var chunks = KnowledgeChunker.split(content);

        assertTrue(chunks.stream().anyMatch(value -> value.contains(marker)));
        assertTrue(chunks.stream().allMatch(value -> value.length() <= KnowledgeChunker.CHUNK_SIZE));
    }
}
