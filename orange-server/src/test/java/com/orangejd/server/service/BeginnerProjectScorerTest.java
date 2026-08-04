package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeginnerProjectScorerTest {
    private final BeginnerProjectScorer scorer = new BeginnerProjectScorer();

    @Test
    void publishesNoDecisionButScoresLightweightTypescriptRepositoryHighly() {
        BeginnerProjectScorer.ScoreResult result = scorer.score(repository(
                "TypeScript", true, false, false, false, false, false));

        assertTrue(result.score() >= 80);
        assertEquals("BEGINNER", result.difficulty());
        assertEquals("CANDIDATE", result.status());
        assertTrue(result.evidence().stream().anyMatch(item -> item.dimensionKey().equals("environment")));
    }

    @Test
    void rejectsArchivedRepositoryEvenWhenOtherSignalsAreStrong() {
        BeginnerProjectScorer.ScoreResult result = scorer.score(repository(
                "TypeScript", true, true, false, false, false, false));

        assertEquals(0, result.score());
        assertEquals("INVALID", result.status());
    }

    @Test
    void penalizesHeavyRuntimeRequirementsDeterministically() {
        BeginnerProjectScorer.ScoreResult light = scorer.score(repository(
                "JavaScript", true, false, false, false, false, false));
        BeginnerProjectScorer.ScoreResult heavy = scorer.score(repository(
                "JavaScript", true, false, true, true, true, true));

        assertEquals(37, light.score() - heavy.score());
        assertTrue(heavy.score() < 65);
    }

    private BeginnerGithubGateway.RepositorySnapshot repository(
            String language, boolean readme, boolean archived, boolean docker,
            boolean database, boolean secrets, boolean external) {
        return new BeginnerGithubGateway.RepositorySnapshot(
                "owner/project", "owner", "project", "https://github.com/owner/project",
                "A small project", "Quick start\nnpm install\nnpm run dev", language,
                List.of("ai-tools"), language + ",React", "npm", ">=20", "MIT",
                500, 20, 5, archived, readme, true, 2, docker, database,
                secrets, external, false, false, "SMALL",
                true, true, true, true, true,
                LocalDateTime.now().minusYears(1), LocalDateTime.now().minusDays(2));
    }
}
