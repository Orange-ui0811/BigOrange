package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonaRecommendationPolicyTest {
    private final PersonaRecommendationPolicy policy = new PersonaRecommendationPolicy();

    @Test
    void avoidanceEntriesAreHardExclusionsInsteadOfPositiveKeywords() {
        Map<PersonaDimension, List<Map<String, Object>>> grouped = Map.of(
                PersonaDimension.GOAL, List.of(Map.of("content", "找到适合自己的项目")),
                PersonaDimension.LEFT_HAND, List.of(Map.of("content", "不想参加比赛，也不要新手入门项目")));

        List<Map<String, Object>> recommendations = policy.recommendations(grouped);

        assertFalse(recommendations.stream().anyMatch(item -> "COMPETITION".equals(item.get("type"))));
        assertFalse(recommendations.stream().anyMatch(item -> "BEGINNER".equals(item.get("type"))));
        assertTrue(recommendations.stream().anyMatch(item -> "PROJECT".equals(item.get("type"))));
    }
}
