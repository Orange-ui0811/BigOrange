package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BeginnerProjectScorer {
    public static final String SCORE_VERSION = "beginner-v3";
    private static final Set<String> PREFERRED_LANGUAGES = Set.of(
            "typescript", "javascript", "html", "css", "python");

    public ScoreResult score(BeginnerGithubGateway.RepositorySnapshot repository) {
        List<Evidence> evidence = new ArrayList<>();
        int score = 35;
        evidence.add(new Evidence("baseline", 35, 35, "Repository metadata is available"));

        String language = normalize(repository.primaryLanguage());
        score += add(evidence, "language", PREFERRED_LANGUAGES.contains(language) ? 15 : 4, 15,
                PREFERRED_LANGUAGES.contains(language)
                        ? "Uses a beginner-friendly or Vibe Coding friendly language"
                        : "Language is less aligned with the preferred beginner stack");
        score += add(evidence, "readme", repository.hasReadme() ? 15 : -35, 15,
                repository.hasReadme() ? "README is available" : "README is missing");
        score += add(evidence, "activity", activityScore(repository.githubUpdatedAt()), 15,
                activityEvidence(repository.githubUpdatedAt()));
        score += add(evidence, "adoption", repository.stars() >= 100 ? 10 : repository.stars() >= 20 ? 6 : 2,
                10, "Stars: " + repository.stars());
        score += add(evidence, "tooling", repository.packageManager() != null ? 5 : 1, 5,
                repository.packageManager() == null ? "No package manager detected"
                        : "Package manager: " + repository.packageManager());
        score += add(evidence, "quick_start", repository.hasQuickStart() ? 10 : -20, 10,
                repository.hasQuickStart() ? "README contains a verifiable quick start"
                        : "No clear quick start was detected");
        boolean licensed = repository.licenseSpdx() != null && !repository.licenseSpdx().isBlank()
                && !"NOASSERTION".equalsIgnoreCase(repository.licenseSpdx());
        score += add(evidence, "license", licensed ? 5 : -10, 5,
                licensed ? "License: " + repository.licenseSpdx() : "Open-source license is unclear");

        int riskPenalty = 0;
        List<String> risks = new ArrayList<>();
        if (repository.requiresDocker()) { riskPenalty -= 12; risks.add("Docker"); }
        if (repository.requiresDatabase()) { riskPenalty -= 15; risks.add("database"); }
        if (repository.requiresSecrets()) { riskPenalty -= 12; risks.add("API key or secret"); }
        if (repository.requiresExternalService()) { riskPenalty -= 8; risks.add("external cloud service"); }
        if (repository.requiresNativeBuild()) { riskPenalty -= 8; risks.add("native build tooling"); }
        if ("LARGE".equals(repository.codebaseSize())) { riskPenalty -= 8; risks.add("large codebase"); }
        score += add(evidence, "environment", riskPenalty, 0,
                risks.isEmpty() ? "No heavy environment dependency detected" : "Detected: " + String.join(", ", risks));

        boolean invalid = repository.archived() || !repository.hasReadme() || repository.requiresGpu();
        if (repository.archived()) evidence.add(new Evidence("availability", -100, 0, "Repository is archived"));
        if (repository.requiresGpu()) evidence.add(new Evidence("availability", -100, 0, "GPU is required"));
        int bounded = invalid ? 0 : Math.max(0, Math.min(100, score));
        String difficulty = bounded >= 80 ? "BEGINNER" : bounded >= 65 ? "EASY" : "MEDIUM";
        String status = invalid ? "INVALID" : "CANDIDATE";
        return new ScoreResult(bounded, difficulty, status, List.copyOf(evidence));
    }

    private int activityScore(LocalDateTime updatedAt) {
        if (updatedAt == null) return 0;
        long days = ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now());
        if (days <= 14) return 15;
        if (days <= 45) return 10;
        if (days <= 120) return 5;
        return -10;
    }

    private String activityEvidence(LocalDateTime updatedAt) {
        if (updatedAt == null) return "Last update time is unavailable";
        return "Last GitHub update: " + updatedAt.toLocalDate();
    }

    private int add(List<Evidence> evidence, String key, int score, int max, String text) {
        evidence.add(new Evidence(key, score, max, text));
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Evidence(String dimensionKey, int score, int maxScore, String evidenceText) {}
    public record ScoreResult(int score, String difficulty, String status, List<Evidence> evidence) {}
}
