package com.orangejd.pojo.entity;

public class CareerRoleScore {
    private Long projectId;
    private String roleCode;
    private Integer roleRelevance;
    private Integer provableAbility;
    private Integer knowledgeCoverage;
    private Integer portfolioCompleteness;
    private Integer interviewValue;
    private Integer totalScore;
    private Integer jobSampleCount;
    private String evidenceJson;
    private String scoreVersion;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Integer getRoleRelevance() { return roleRelevance; }
    public void setRoleRelevance(Integer roleRelevance) { this.roleRelevance = roleRelevance; }
    public Integer getProvableAbility() { return provableAbility; }
    public void setProvableAbility(Integer provableAbility) { this.provableAbility = provableAbility; }
    public Integer getKnowledgeCoverage() { return knowledgeCoverage; }
    public void setKnowledgeCoverage(Integer knowledgeCoverage) { this.knowledgeCoverage = knowledgeCoverage; }
    public Integer getPortfolioCompleteness() { return portfolioCompleteness; }
    public void setPortfolioCompleteness(Integer portfolioCompleteness) { this.portfolioCompleteness = portfolioCompleteness; }
    public Integer getInterviewValue() { return interviewValue; }
    public void setInterviewValue(Integer interviewValue) { this.interviewValue = interviewValue; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getJobSampleCount() { return jobSampleCount; }
    public void setJobSampleCount(Integer jobSampleCount) { this.jobSampleCount = jobSampleCount; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getScoreVersion() { return scoreVersion; }
    public void setScoreVersion(String scoreVersion) { this.scoreVersion = scoreVersion; }
}
