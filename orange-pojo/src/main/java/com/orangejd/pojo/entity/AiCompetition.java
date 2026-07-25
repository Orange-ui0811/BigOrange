package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class AiCompetition {
    private Long id;
    private String name;
    private String sourceUrl;
    private String normalizedUrl;
    private String summary;
    private String organizer;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String auditStatus;
    private String submissionSource;
    private Long submitterId;
    private String submitterIp;
    private Long reviewerId;
    private String rejectReason;
    private Boolean rewardGranted;
    private Integer rewardQuota;
    private LocalDateTime approvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getSubmissionSource() { return submissionSource; }
    public void setSubmissionSource(String submissionSource) { this.submissionSource = submissionSource; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public String getSubmitterIp() { return submitterIp; }
    public void setSubmitterIp(String submitterIp) { this.submitterIp = submitterIp; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Boolean getRewardGranted() { return rewardGranted; }
    public void setRewardGranted(Boolean rewardGranted) { this.rewardGranted = rewardGranted; }
    public Integer getRewardQuota() { return rewardQuota; }
    public void setRewardQuota(Integer rewardQuota) { this.rewardQuota = rewardQuota; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
