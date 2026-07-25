package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class JobPosting {
    private Long id;
    private String company;
    private String position;
    private String direction;
    private String content;
    private String displayContent;
    private String auditStatus;
    private Long submitterId;
    private String submitterIp;
    private String submitterRole;
    private String rejectReason;
    private String auditSource;
    private Double auditConfidence;
    private String auditReason;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDisplayContent() { return displayContent; }
    public void setDisplayContent(String displayContent) { this.displayContent = displayContent; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public String getSubmitterIp() { return submitterIp; }
    public void setSubmitterIp(String submitterIp) { this.submitterIp = submitterIp; }
    public String getSubmitterRole() { return submitterRole; }
    public void setSubmitterRole(String submitterRole) { this.submitterRole = submitterRole; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getAuditSource() { return auditSource; }
    public void setAuditSource(String auditSource) { this.auditSource = auditSource; }
    public Double getAuditConfidence() { return auditConfidence; }
    public void setAuditConfidence(Double auditConfidence) { this.auditConfidence = auditConfidence; }
    public String getAuditReason() { return auditReason; }
    public void setAuditReason(String auditReason) { this.auditReason = auditReason; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
