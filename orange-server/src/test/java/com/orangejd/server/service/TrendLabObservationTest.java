package com.orangejd.server.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TrendLabObservationTest {
    @Test void failureIsRecordedAndThreadLocalIsCleared() {
        TrendLabObservation old;
        try(var observation=TrendLabObservation.open()) {
            old=observation;
            assertThrows(IllegalArgumentException.class,()->TrendLabObservation.measure("failure",()->{throw new IllegalArgumentException();}));
            assertEquals("FAILED",observation.stages.getFirst().get("status"));
        }
        TrendLabObservation.measure("normal",()->true);
        assertEquals(1,old.stages.size());
        try(var next=TrendLabObservation.open()) { assertTrue(next.stages.isEmpty()); }
    }
    @Test void ablationPreservesSystemBusinessAndOriginalRequest() {
        var body=Map.<String,Object>of("messages",List.of(Map.of("role","system","content","policy"),Map.of("role","user","content","profile\n\n## 知识库参考\nevidence\n\n## 本次匹配的新手项目\nreal candidates")));
        var removed=TrendRagABPreviewService.withoutKnowledge(body,"evidence");
        assertEquals("policy",removed.getFirst().get("content"));
        assertEquals("profile\n\n## 本次匹配的新手项目\nreal candidates",removed.get(1).get("content"));
        assertTrue(body.toString().contains("evidence"));
    }
}
