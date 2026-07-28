package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class TrendHistory {
    private Long id;
    private Long accountId;
    private String topics;
    private String purpose;
    private String experience;
    private String languages;
    private String timeCommitment;
    private String customTopics;
    private String customPurpose;
    private String customExp;
    private String customLang;
    private String customTime;
    private Boolean useRag;
    private String resultJson;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getTopics() { return topics; }
    public void setTopics(String topics) { this.topics = topics; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public String getTimeCommitment() { return timeCommitment; }
    public void setTimeCommitment(String timeCommitment) { this.timeCommitment = timeCommitment; }
    public String getCustomTopics() { return customTopics; }
    public void setCustomTopics(String customTopics) { this.customTopics = customTopics; }
    public String getCustomPurpose() { return customPurpose; }
    public void setCustomPurpose(String customPurpose) { this.customPurpose = customPurpose; }
    public String getCustomExp() { return customExp; }
    public void setCustomExp(String customExp) { this.customExp = customExp; }
    public String getCustomLang() { return customLang; }
    public void setCustomLang(String customLang) { this.customLang = customLang; }
    public String getCustomTime() { return customTime; }
    public void setCustomTime(String customTime) { this.customTime = customTime; }
    public Boolean getUseRag() { return useRag; }
    public void setUseRag(Boolean useRag) { this.useRag = useRag; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
