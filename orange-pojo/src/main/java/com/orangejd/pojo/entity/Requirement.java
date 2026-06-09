package com.orangejd.pojo.entity;

public class Requirement extends BaseEntity {
    private Long publisherId;
    private String title;
    private String detail;
    private Long categoryId;
    private java.math.BigDecimal budgetMin;
    private java.math.BigDecimal budgetMax;
    private String auditStatus;
    private String requirementStatus;
    private String visibilityStatus;
    private String publisherName;

    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public java.math.BigDecimal getBudgetMin() { return budgetMin; }
    public void setBudgetMin(java.math.BigDecimal budgetMin) { this.budgetMin = budgetMin; }
    public java.math.BigDecimal getBudgetMax() { return budgetMax; }
    public void setBudgetMax(java.math.BigDecimal budgetMax) { this.budgetMax = budgetMax; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getRequirementStatus() { return requirementStatus; }
    public void setRequirementStatus(String requirementStatus) { this.requirementStatus = requirementStatus; }
    public String getVisibilityStatus() { return visibilityStatus; }
    public void setVisibilityStatus(String visibilityStatus) { this.visibilityStatus = visibilityStatus; }
    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }
}
