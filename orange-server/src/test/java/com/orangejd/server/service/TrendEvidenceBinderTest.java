package com.orangejd.server.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrendEvidenceBinderTest {
    @Test void matchesOnlyCurrentEvidenceAndReplacesInventedIds() {
        var retrieval = new TrendContextBuilder.Retrieval("context", Map.of("evidence", List.of(
                Map.of("evidenceId", "E1", "text", "repo https://github.com/a/b"))));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("references", List.of(Map.of("url", "https://github.com/A/B.git", "name", "fake-name"),
                Map.of("url", "https://github.com/x/y", "name", "invented")));
        item.put("evidenceIds", List.of("E999"));
        Map<String, Object> result = new LinkedHashMap<>(Map.of("recommendations", List.of(item)));
        TrendEvidenceBinder.bind(result, retrieval, List.of(), List.of());
        assertEquals("PARTIAL", item.get("evidenceStatus"));
        assertEquals(List.of("E1"), item.get("evidenceIds"));
        assertEquals(1, ((List<?>) item.get("references")).size());
        assertEquals(1, item.get("unsupportedReferenceCount"));
    }
    @Test void rejectsLookalikeHostAndNonRepositoryLinks() {
        assertNull(TrendEvidenceBinder.canonical("https://github.com.evil.test/a/b"));
        assertNull(TrendEvidenceBinder.canonical("https://github.com/a/b/issues"));
        assertNull(TrendEvidenceBinder.canonical("https://github.com/user-attachments/assets"));
        assertNull(TrendEvidenceBinder.canonical("https://github.com/topics/java"));
        assertNull(TrendEvidenceBinder.canonical("javascript:alert(1)"));
    }
    @Test void legacyBodyLinkMustMatchDocumentTitle() {
        var valid = new org.springframework.ai.document.Document("1", "repo https://github.com/andy/customer-service-ai",
                Map.of("docTitle", "customer-service-ai"));
        var dependency = new org.springframework.ai.document.Document("2", "based on https://github.com/THUDM/ChatGLM-6B",
                Map.of("docTitle", "on linux or macos"));
        assertEquals("https://github.com/andy/customer-service-ai", TrendEvidenceBinder.repositoryUrl(valid));
        assertNull(TrendEvidenceBinder.repositoryUrl(dependency));
    }
    @Test void removesAnUnsupportedRecommendationWhenVerifiedCandidatesExist() {
        var retrieval = new TrendContextBuilder.Retrieval("context", Map.of("evidence", List.of(
                Map.of("evidenceId", "E1", "repositoryUrl", "https://github.com/a/b", "text", "project"))));
        Map<String, Object> supported = new LinkedHashMap<>(Map.of("candidateId", "E1"));
        Map<String, Object> invented = new LinkedHashMap<>(Map.of("candidateId", "E999"));
        Map<String, Object> result = new LinkedHashMap<>(Map.of("recommendations", List.of(supported, invented)));

        TrendEvidenceBinder.bind(result, retrieval, List.of(), List.of());

        assertEquals(List.of(supported), result.get("recommendations"));
        assertEquals("VERIFIED_CANDIDATE", supported.get("evidenceStatus"));
        assertEquals("https://github.com/a/b",
                ((Map<?, ?>) ((List<?>) supported.get("references")).getFirst()).get("url"));
    }
}
