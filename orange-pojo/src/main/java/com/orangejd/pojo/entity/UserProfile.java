package com.orangejd.pojo.entity;

public class UserProfile extends BaseEntity {
    private Long accountId;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String skills;
    private String contactIntro;
    private Integer homepageVisible;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getContactIntro() {
        return contactIntro;
    }

    public void setContactIntro(String contactIntro) {
        this.contactIntro = contactIntro;
    }

    public Integer getHomepageVisible() {
        return homepageVisible;
    }

    public void setHomepageVisible(Integer homepageVisible) {
        this.homepageVisible = homepageVisible;
    }
}
