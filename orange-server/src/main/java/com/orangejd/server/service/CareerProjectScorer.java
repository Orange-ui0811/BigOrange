package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CareerProjectScorer {
    public static final String SCORE_VERSION = "career-v1";

    private final CareerRoleTaxonomy taxonomy;

    public CareerProjectScorer(CareerRoleTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    public ScoreResult score(BeginnerGithubGateway.RepositorySnapshot repository,
                             Map<String, Integer> jobSamples) {
        String searchable = String.join(" ", safe(repository.fullName()), safe(repository.description()),
                safe(repository.readmeExcerpt()), safe(repository.techStack()),
                String.join(" ", repository.topics())).toLowerCase(Locale.ROOT);
        List<RoleScore> roleScores = new ArrayList<>();
        for (CareerRoleTaxonomy.RoleCategory category : taxonomy.categories()) {
            int relevance = roleRelevance(category.code(), searchable, jobSamples.getOrDefault(category.code(), 0));
            int proof = provableAbility(repository);
            int coverage = knowledgeCoverage(repository);
            int completeness = portfolioCompleteness(repository);
            int interview = interviewValue(repository);
            int total = relevance + proof + coverage + completeness + interview;
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("roleRelevance", "Matched role vocabulary and "
                    + jobSamples.getOrDefault(category.code(), 0) + " approved job samples");
            evidence.put("provableAbility", proofEvidence(repository));
            evidence.put("knowledgeCoverage", coverageEvidence(repository));
            evidence.put("portfolioCompleteness", completenessEvidence(repository));
            evidence.put("interviewValue", interviewEvidence(repository));
            roleScores.add(new RoleScore(category.code(), relevance, proof, coverage,
                    completeness, interview, total, jobSamples.getOrDefault(category.code(), 0), evidence));
        }
        roleScores.sort(Comparator.comparingInt(RoleScore::totalScore).reversed()
                .thenComparing(RoleScore::roleCode));
        int top = roleScores.get(0).totalScore();
        int second = roleScores.size() > 1 ? roleScores.get(1).totalScore() : top;
        double remainingAverage = roleScores.size() <= 2 ? second
                : roleScores.subList(2, roleScores.size()).stream().mapToInt(RoleScore::totalScore).average().orElse(second);
        int overall = clamp((int) Math.round(top * 0.7 + second * 0.2 + remainingAverage * 0.1), 0, 100);
        String status = repository.archived() || !repository.hasReadme() ? "INVALID" : "CANDIDATE";
        return new ScoreResult(overall, roleScores.get(0).roleCode(), difficulty(repository), status, roleScores);
    }

    private int roleRelevance(String roleCode, String searchable, int sampleCount) {
        int hits = 0;
        for (String keyword : taxonomy.keywords(roleCode)) if (searchable.contains(keyword)) hits++;
        return clamp(4 + Math.min(12, hits * 3) + Math.min(4, sampleCount / 2), 0, 20);
    }

    private int provableAbility(BeginnerGithubGateway.RepositorySnapshot value) {
        int score = 3 + (value.hasTests() ? 7 : 0) + (value.hasCi() ? 4 : 0)
                + (value.hasReleaseAutomation() ? 3 : 0) + (value.stars() >= 100 ? 3 : 1);
        if (!value.hasTests()) score = Math.min(score, 12);
        return clamp(score, 0, 20);
    }

    private int knowledgeCoverage(BeginnerGithubGateway.RepositorySnapshot value) {
        int stackItems = safe(value.techStack()).isBlank() ? 0 : safe(value.techStack()).split(",").length;
        int score = 4 + Math.min(8, stackItems * 2)
                + (value.requiresDatabase() ? 2 : 0) + (value.requiresDocker() ? 2 : 0)
                + (value.requiresExternalService() ? 2 : 0) + (value.hasArchitectureDocs() ? 2 : 0);
        return clamp(score, 0, 20);
    }

    private int portfolioCompleteness(BeginnerGithubGateway.RepositorySnapshot value) {
        int score = (value.hasReadme() ? 4 : 0) + (value.hasQuickStart() ? 5 : 0)
                + (value.hasDeployment() ? 4 : 0) + (safe(value.licenseSpdx()).isBlank() ? 0 : 2)
                + (recent(value.githubUpdatedAt()) ? 3 : 0) + ("LARGE".equals(value.codebaseSize()) ? 0 : 2);
        if (!value.hasQuickStart()) score = Math.min(score, 12);
        return clamp(score, 0, 20);
    }

    private int interviewValue(BeginnerGithubGateway.RepositorySnapshot value) {
        int score = 4 + (value.hasArchitectureDocs() ? 5 : 0) + (value.hasTests() ? 3 : 0)
                + (value.hasCi() ? 2 : 0) + (value.requiresDatabase() || value.requiresDocker() ? 3 : 0)
                + ("MEDIUM".equals(value.codebaseSize()) || "LARGE".equals(value.codebaseSize()) ? 3 : 1)
                + (value.openIssues() > 0 ? 2 : 0);
        return clamp(score, 0, 20);
    }

    private String difficulty(BeginnerGithubGateway.RepositorySnapshot value) {
        if (value.requiresGpu() || value.requiresNativeBuild() || "LARGE".equals(value.codebaseSize())) return "ADVANCED";
        int dependencies = (value.requiresDocker() ? 1 : 0) + (value.requiresDatabase() ? 1 : 0)
                + (value.requiresSecrets() ? 1 : 0) + (value.requiresExternalService() ? 1 : 0);
        return dependencies >= 2 || "MEDIUM".equals(value.codebaseSize()) ? "INTERMEDIATE" : "ENTRY";
    }

    private boolean recent(LocalDateTime value) {
        return value != null && ChronoUnit.DAYS.between(value, LocalDateTime.now()) <= 90;
    }

    private String proofEvidence(BeginnerGithubGateway.RepositorySnapshot value) {
        return "tests=" + value.hasTests() + ", ci=" + value.hasCi()
                + ", releaseAutomation=" + value.hasReleaseAutomation();
    }

    private String coverageEvidence(BeginnerGithubGateway.RepositorySnapshot value) {
        return "stack=" + safe(value.techStack()) + ", database=" + value.requiresDatabase()
                + ", docker=" + value.requiresDocker();
    }

    private String completenessEvidence(BeginnerGithubGateway.RepositorySnapshot value) {
        return "quickStart=" + value.hasQuickStart() + ", deployment=" + value.hasDeployment()
                + ", license=" + safe(value.licenseSpdx());
    }

    private String interviewEvidence(BeginnerGithubGateway.RepositorySnapshot value) {
        return "architectureDocs=" + value.hasArchitectureDocs() + ", size=" + safe(value.codebaseSize())
                + ", openIssues=" + value.openIssues();
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private String safe(String value) { return value == null ? "" : value; }

    public record RoleScore(String roleCode, int roleRelevance, int provableAbility,
                            int knowledgeCoverage, int portfolioCompleteness, int interviewValue,
                            int totalScore, int jobSampleCount, Map<String, String> evidence) {}

    public record ScoreResult(int overallScore, String bestRoleCode, String difficulty,
                              String status, List<RoleScore> roleScores) {}
}
