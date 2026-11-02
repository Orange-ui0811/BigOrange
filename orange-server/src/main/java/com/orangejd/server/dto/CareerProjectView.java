package com.orangejd.server.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CareerProjectView(
        Long id,
        String fullName,
        String ownerName,
        String repositoryName,
        String htmlUrl,
        String descriptionOriginal,
        String descriptionZh,
        String primaryLanguage,
        String techStack,
        String licenseSpdx,
        int stars,
        boolean hasQuickStart,
        boolean requiresDocker,
        boolean requiresDatabase,
        boolean requiresSecrets,
        boolean requiresExternalService,
        String codebaseSize,
        int overallScore,
        String bestRoleCode,
        String bestRoleName,
        String difficulty,
        String summaryZh,
        String careerValue,
        String proofPoints,
        String interviewTopics,
        String modificationPlan,
        String mainRisks,
        String estimatedDuration,
        String status,
        LocalDateTime githubUpdatedAt,
        LocalDateTime lastVerifiedAt,
        List<CareerRoleScoreView> roleScores) {
}
