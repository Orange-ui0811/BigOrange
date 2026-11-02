package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerProjectScorerTest {
    private final CareerProjectScorer scorer = new CareerProjectScorer(new CareerRoleTaxonomy());

    @Test
    void ranksTestedAgentRepositoryForApplicationRoles() {
        CareerProjectScorer.ScoreResult result = scorer.score(repository(
                "Agent RAG workflow with tool calling", true, true, true, true, true, true),
                Map.of(CareerRoleTaxonomy.AI_APPLICATION_AGENT, 8));

        assertEquals(CareerRoleTaxonomy.AI_APPLICATION_AGENT, result.bestRoleCode());
        assertTrue(result.overallScore() >= 65);
        assertEquals("CANDIDATE", result.status());
    }

    @Test
    void missingTestsAndQuickStartCapsProofAndCompleteness() {
        CareerProjectScorer.ScoreResult result = scorer.score(repository(
                "Agent RAG platform", false, false, true, true, true, true), Map.of());
        CareerProjectScorer.RoleScore top = result.roleScores().get(0);

        assertTrue(top.provableAbility() <= 12);
        assertTrue(top.portfolioCompleteness() <= 12);
    }

    @Test
    void archivedRepositoryIsNeverPublishable() {
        BeginnerGithubGateway.RepositorySnapshot value = repository(
                "React AI application", true, true, true, true, true, true);
        value = copyWithArchived(value);

        assertEquals("INVALID", scorer.score(value, Map.of()).status());
    }

    @Test
    void acceptsStructuredAiNarrativeListsWithoutDroppingContent() throws Exception {
        var value = new ObjectMapper().readTree("{\"proofPoints\":[\"补充测试\",\"完成部署\"]}");

        assertEquals("- 补充测试\n- 完成部署", CareerProjectService.content(value, "proofPoints"));
    }

    private BeginnerGithubGateway.RepositorySnapshot repository(String description, boolean tests,
            boolean quickStart, boolean ci, boolean deployment, boolean architecture, boolean release) {
        return new BeginnerGithubGateway.RepositorySnapshot(
                "owner/project", "owner", "project", "https://github.com/owner/project",
                description, "Quick start npm install test architecture deploy", "TypeScript",
                List.of("agent", "rag"), "TypeScript,React,Node.js", "npm", ">=20", "MIT",
                500, 20, 5, false, true, quickStart, quickStart ? 2 : 0,
                false, false, false, false, false, false, "SMALL",
                tests, ci, deployment, architecture, release,
                LocalDateTime.now().minusYears(1), LocalDateTime.now().minusDays(2));
    }

    private BeginnerGithubGateway.RepositorySnapshot copyWithArchived(BeginnerGithubGateway.RepositorySnapshot value) {
        return new BeginnerGithubGateway.RepositorySnapshot(
                value.fullName(), value.ownerName(), value.repositoryName(), value.htmlUrl(), value.description(),
                value.readmeExcerpt(), value.primaryLanguage(), value.topics(), value.techStack(), value.packageManager(),
                value.nodeVersion(), value.licenseSpdx(), value.stars(), value.forks(), value.openIssues(), true,
                value.hasReadme(), value.hasQuickStart(), value.setupSteps(), value.requiresDocker(),
                value.requiresDatabase(), value.requiresSecrets(), value.requiresExternalService(), value.requiresGpu(),
                value.requiresNativeBuild(), value.codebaseSize(), value.hasTests(), value.hasCi(), value.hasDeployment(),
                value.hasArchitectureDocs(), value.hasReleaseAutomation(), value.githubCreatedAt(), value.githubUpdatedAt());
    }
}
