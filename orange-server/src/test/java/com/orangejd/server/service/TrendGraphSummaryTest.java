package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TrendGraphSummaryTest {
    @Test
    void includesOnlyNodesRelatedToTheRequestedTopic() {
        var builder = new TrendContextBuilder(mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
        Map<String, Object> spring = node("spring-boot", "Spring Boot", 80);
        Map<String, Object> vue = node("vue", "Vue", 90);
        Map<String, Object> graph = Map.of(
                "thisWeek", Map.of("nodes", List.of(spring, vue)),
                "lastWeek", Map.of("nodes", List.of()));
        var input = new TrendAnalysisInput(List.of("Spring Boot后端开发"), "学习", "新手",
                List.of("Java"), "5h", "", "", "", "", "", true, false, false, false);

        String summary = builder.graphSummary(graph, input);

        assertTrue(summary.contains("Spring Boot"));
        assertFalse(summary.contains("**Vue**"));
    }

    private Map<String, Object> node(String id, String name, int score) {
        return Map.of("id", id, "name", name, "group", "framework", "totalStars", 100L,
                "repoCount", 5, "metrics", Map.of("opportunityScore", score, "growth", 2,
                        "crowdedness", "中", "difficulty", "中", "insight", "test"));
    }
}
