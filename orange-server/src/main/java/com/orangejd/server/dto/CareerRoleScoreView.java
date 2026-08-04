package com.orangejd.server.dto;

public record CareerRoleScoreView(
        String roleCode,
        String roleName,
        int roleRelevance,
        int provableAbility,
        int knowledgeCoverage,
        int portfolioCompleteness,
        int interviewValue,
        int totalScore,
        int jobSampleCount,
        String evidenceJson) {
}
