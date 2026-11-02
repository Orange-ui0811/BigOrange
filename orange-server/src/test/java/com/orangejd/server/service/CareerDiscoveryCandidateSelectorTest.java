package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerDiscoveryCandidateSelectorTest {
    private final CareerDiscoveryCandidateSelector selector = new CareerDiscoveryCandidateSelector();

    @Test
    void preservesCapabilityDiversityInsteadOfReturningOnePopularCategory() {
        List<CareerDiscoveryCandidateSelector.Candidate> candidates = List.of(
                candidate("agent/one", "AGENT_NATIVE_APP", 90),
                candidate("agent/two", "AGENT_APPLICATION", 89),
                candidate("knowledge/one", "KNOWLEDGE_TO_SKILL", 80),
                candidate("design/one", "AI_DESIGN_ASSISTANCE", 70));

        assertEquals(List.of("agent/one", "knowledge/one", "design/one"),
                selector.select(candidates, 3));
    }

    @Test
    void deduplicatesRepositoriesFoundByMultipleSemanticQueries() {
        List<CareerDiscoveryCandidateSelector.Candidate> candidates = List.of(
                candidate("owner/shared", "AGENT_APPLICATION", 70),
                candidate("owner/shared", "FULLSTACK_PRODUCT", 90),
                candidate("owner/other", "KNOWLEDGE_TOOL", 60));

        assertEquals(List.of("owner/other", "owner/shared"), selector.select(candidates, 2));
    }

    private CareerDiscoveryCandidateSelector.Candidate candidate(String name, String category, int score) {
        return new CareerDiscoveryCandidateSelector.Candidate(name, category, score);
    }
}
