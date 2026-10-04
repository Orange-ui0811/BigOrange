package com.orangejd.server.service;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class TrendRedisMetadataTest {
    @Test void restoresWhitelistedFieldsAndPreservesScoreAndText() {
        Document before = Document.builder().id("knowledge-1-0").text("original")
                .metadata(Map.of("distance", .2)).score(.8).build();
        Document after = TrendRedisMetadata.merge(before, Map.of("docId", "1", "chunkIndex", 0.0,
                "source", "GITHUB", "docTitle", "Example", "embedding", new float[512], "content", "changed"));
        assertEquals("original", after.getText());
        assertEquals(.8, after.getScore());
        assertEquals("GITHUB", after.getMetadata().get("source"));
        assertEquals(0, after.getMetadata().get("chunkIndex"));
        assertFalse(after.getMetadata().containsKey("embedding"));
        assertFalse(after.getMetadata().containsKey("content"));
        assertEquals(.2, after.getMetadata().get("distance"));
    }
    @Test void missingJsonDoesNotInventMetadata() {
        Document before = new Document("body");
        assertSame(before, TrendRedisMetadata.merge(before, null));
    }
}
