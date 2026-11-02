package com.orangejd.pojo.dto;

import java.util.List;

public class TrendProfileDTO {
    private List<String> topics;
    private String purpose;
    private String experience;
    private List<String> languages;
    private String timeCommitment;
    // 自定义补充字段
    private String customTopics;
    private String customPurpose;
    private String customExp;
    private String customLang;
    private String customTime;
    private Boolean useRag;
    private Boolean usePersona;
    private Boolean preferBeginnerProjects;
    private Boolean preferCareerProjects;

    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
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
    public Boolean getUsePersona() { return usePersona; }
    public void setUsePersona(Boolean usePersona) { this.usePersona = usePersona; }
    public Boolean getPreferBeginnerProjects() { return preferBeginnerProjects; }
    public void setPreferBeginnerProjects(Boolean preferBeginnerProjects) { this.preferBeginnerProjects = preferBeginnerProjects; }
    public Boolean getPreferCareerProjects() { return preferCareerProjects; }
    public void setPreferCareerProjects(Boolean preferCareerProjects) { this.preferCareerProjects = preferCareerProjects; }
}
