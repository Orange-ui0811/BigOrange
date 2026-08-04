package com.orangejd.server.service;

import java.time.LocalDateTime;
import java.util.List;

public interface BeginnerGithubGateway {
    List<String> discoverRepositories(int limit);

    List<String> discoverCareerRepositories(int limit);

    RepositorySnapshot inspectRepository(String fullName);

    record RepositorySnapshot(
            String fullName,
            String ownerName,
            String repositoryName,
            String htmlUrl,
            String description,
            String readmeExcerpt,
            String primaryLanguage,
            List<String> topics,
            String techStack,
            String packageManager,
            String nodeVersion,
            String licenseSpdx,
            int stars,
            int forks,
            int openIssues,
            boolean archived,
            boolean hasReadme,
            boolean hasQuickStart,
            int setupSteps,
            boolean requiresDocker,
            boolean requiresDatabase,
            boolean requiresSecrets,
            boolean requiresExternalService,
            boolean requiresGpu,
            boolean requiresNativeBuild,
            String codebaseSize,
            boolean hasTests,
            boolean hasCi,
            boolean hasDeployment,
            boolean hasArchitectureDocs,
            boolean hasReleaseAutomation,
            LocalDateTime githubCreatedAt,
            LocalDateTime githubUpdatedAt) {
    }
}
