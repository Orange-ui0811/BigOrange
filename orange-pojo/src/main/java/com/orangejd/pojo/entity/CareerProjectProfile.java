package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class CareerProjectProfile {
    private Long projectId;
    private String fullName;
    private String ownerName;
    private String repositoryName;
    private String htmlUrl;
    private String descriptionOriginal;
    private String descriptionZh;
    private String primaryLanguage;
    private String techStack;
    private String licenseSpdx;
    private Integer stars;
    private Boolean archived;
    private Boolean hasReadme;
    private Boolean hasQuickStart;
    private Boolean requiresDocker;
    private Boolean requiresDatabase;
    private Boolean requiresSecrets;
    private Boolean requiresExternalService;
    private String codebaseSize;
    private Integer overallScore;
    private String bestRoleCode;
    private String difficulty;
    private String summaryZh;
    private String careerValue;
    private String proofPoints;
    private String interviewTopics;
    private String modificationPlan;
    private String mainRisks;
    private String estimatedDuration;
    private String status;
    private String hiddenReason;
    private String scoreVersion;
    private String taxonomyVersion;
    private String contentHash;
    private LocalDateTime githubUpdatedAt;
    private LocalDateTime lastVerifiedAt;
    private Long sourceRunId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getDescriptionOriginal() { return descriptionOriginal; }
    public void setDescriptionOriginal(String descriptionOriginal) { this.descriptionOriginal = descriptionOriginal; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String descriptionZh) { this.descriptionZh = descriptionZh; }
    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }
    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getLicenseSpdx() { return licenseSpdx; }
    public void setLicenseSpdx(String licenseSpdx) { this.licenseSpdx = licenseSpdx; }
    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getHasReadme() { return hasReadme; }
    public void setHasReadme(Boolean hasReadme) { this.hasReadme = hasReadme; }
    public Boolean getHasQuickStart() { return hasQuickStart; }
    public void setHasQuickStart(Boolean hasQuickStart) { this.hasQuickStart = hasQuickStart; }
    public Boolean getRequiresDocker() { return requiresDocker; }
    public void setRequiresDocker(Boolean requiresDocker) { this.requiresDocker = requiresDocker; }
    public Boolean getRequiresDatabase() { return requiresDatabase; }
    public void setRequiresDatabase(Boolean requiresDatabase) { this.requiresDatabase = requiresDatabase; }
    public Boolean getRequiresSecrets() { return requiresSecrets; }
    public void setRequiresSecrets(Boolean requiresSecrets) { this.requiresSecrets = requiresSecrets; }
    public Boolean getRequiresExternalService() { return requiresExternalService; }
    public void setRequiresExternalService(Boolean requiresExternalService) { this.requiresExternalService = requiresExternalService; }
    public String getCodebaseSize() { return codebaseSize; }
    public void setCodebaseSize(String codebaseSize) { this.codebaseSize = codebaseSize; }
    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }
    public String getBestRoleCode() { return bestRoleCode; }
    public void setBestRoleCode(String bestRoleCode) { this.bestRoleCode = bestRoleCode; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getSummaryZh() { return summaryZh; }
    public void setSummaryZh(String summaryZh) { this.summaryZh = summaryZh; }
    public String getCareerValue() { return careerValue; }
    public void setCareerValue(String careerValue) { this.careerValue = careerValue; }
    public String getProofPoints() { return proofPoints; }
    public void setProofPoints(String proofPoints) { this.proofPoints = proofPoints; }
    public String getInterviewTopics() { return interviewTopics; }
    public void setInterviewTopics(String interviewTopics) { this.interviewTopics = interviewTopics; }
    public String getModificationPlan() { return modificationPlan; }
    public void setModificationPlan(String modificationPlan) { this.modificationPlan = modificationPlan; }
    public String getMainRisks() { return mainRisks; }
    public void setMainRisks(String mainRisks) { this.mainRisks = mainRisks; }
    public String getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHiddenReason() { return hiddenReason; }
    public void setHiddenReason(String hiddenReason) { this.hiddenReason = hiddenReason; }
    public String getScoreVersion() { return scoreVersion; }
    public void setScoreVersion(String scoreVersion) { this.scoreVersion = scoreVersion; }
    public String getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(String taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public LocalDateTime getGithubUpdatedAt() { return githubUpdatedAt; }
    public void setGithubUpdatedAt(LocalDateTime githubUpdatedAt) { this.githubUpdatedAt = githubUpdatedAt; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
    public Long getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(Long sourceRunId) { this.sourceRunId = sourceRunId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
