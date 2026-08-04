package com.orangejd.server.dto;

import com.orangejd.pojo.entity.BeginnerProject;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record BeginnerProjectView(
        Long id,
        String fullName,
        String repositoryName,
        String ownerName,
        String htmlUrl,
        String descriptionZh,
        String descriptionOriginal,
        String weeklyPerformance,
        String whatIsIt,
        String beginnerReason,
        String modificationIdeas,
        String mainRisks,
        String finalRecommendation,
        String primaryLanguage,
        List<String> topics,
        String techStack,
        String packageManager,
        String nodeVersion,
        String licenseSpdx,
        int stars,
        int forks,
        int openIssues,
        boolean requiresDocker,
        boolean requiresDatabase,
        boolean requiresSecrets,
        boolean requiresExternalService,
        boolean requiresGpu,
        boolean requiresNativeBuild,
        boolean hasQuickStart,
        int setupSteps,
        String codebaseSize,
        String difficulty,
        String estimatedDuration,
        int beginnerScore,
        int editorialScore,
        int vibeCodingFit,
        int showcaseValue,
        int extensionPotential,
        int scopeClarity,
        int contentPotential,
        String scoreVersion,
        String status,
        String hiddenReason,
        LocalDateTime githubUpdatedAt,
        LocalDateTime lastVerifiedAt) {

    public static BeginnerProjectView from(BeginnerProject project, List<String> topics) {
        return new BeginnerProjectView(
                project.getId(), project.getFullName(), project.getRepositoryName(), project.getOwnerName(),
                project.getHtmlUrl(), project.getDescriptionZh(), project.getDescriptionOriginal(),
                weeklyPerformance(project), project.getWhatIsIt(), project.getBeginnerReason(), project.getModificationIdeas(),
                project.getMainRisks(), project.getFinalRecommendation(), project.getPrimaryLanguage(),
                topics, project.getTechStack(), project.getPackageManager(), project.getNodeVersion(), project.getLicenseSpdx(),
                value(project.getStars()), value(project.getForks()), value(project.getOpenIssues()),
                yes(project.getRequiresDocker()), yes(project.getRequiresDatabase()),
                yes(project.getRequiresSecrets()), yes(project.getRequiresExternalService()),
                yes(project.getRequiresGpu()), yes(project.getRequiresNativeBuild()),
                yes(project.getHasQuickStart()), value(project.getSetupSteps()), project.getCodebaseSize(),
                project.getDifficulty(), project.getEstimatedDuration(), value(project.getBeginnerScore()),
                value(project.getEditorialScore()), value(project.getVibeCodingFit()), value(project.getShowcaseValue()),
                value(project.getExtensionPotential()), value(project.getScopeClarity()), value(project.getContentPotential()),
                project.getScoreVersion(), project.getStatus(), project.getHiddenReason(),
                project.getGithubUpdatedAt(), project.getLastVerifiedAt());
    }

    private static boolean yes(Boolean value) { return Boolean.TRUE.equals(value); }
    private static int value(Integer value) { return value == null ? 0 : value; }

    private static String weeklyPerformance(BeginnerProject project) {
        LocalDateTime updatedAt = project.getGithubUpdatedAt();
        long days = updatedAt == null ? Long.MAX_VALUE
                : Math.max(0, ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now()));
        String activity = days <= 7 ? "本周仍有更新"
                : days <= 30 ? "近 30 天有更新" : "近期更新较少";
        return activity + "，当前 " + value(project.getStars()) + " Star；最后更新于 "
                + (updatedAt == null ? "待核验" : updatedAt.toLocalDate());
    }
}
