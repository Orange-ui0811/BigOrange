package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendCandidateMatcherTest {
    @Test
    void requiresTopicMatchButRewardsLanguageMatch() {
        int relevant = TrendCandidateMatcher.score(List.of("Python数据分析"), List.of("Python"),
                "Pandas 数据分析与可视化项目", "Python");
        int wrongTopic = TrendCandidateMatcher.score(List.of("Python数据分析"), List.of("Python"),
                "LLM Agent 知识库项目", "Python");

        assertTrue(relevant > 0);
        assertTrue(wrongTopic < 0);
        assertTrue(TrendCandidateMatcher.score(List.of("RAG"), List.of("Java"),
                "Java storage engine", "Java") < 0);
    }
}
