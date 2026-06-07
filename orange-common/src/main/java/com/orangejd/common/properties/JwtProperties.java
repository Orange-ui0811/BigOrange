package com.orangejd.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "orange.jwt")
public class JwtProperties {
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;
    private String appSecretKey;
    private long appTtl;
    private String appTokenName;

    public String getAdminSecretKey() {
        return adminSecretKey;
    }

    public void setAdminSecretKey(String adminSecretKey) {
        this.adminSecretKey = adminSecretKey;
    }

    public long getAdminTtl() {
        return adminTtl;
    }

    public void setAdminTtl(long adminTtl) {
        this.adminTtl = adminTtl;
    }

    public String getAdminTokenName() {
        return adminTokenName;
    }

    public void setAdminTokenName(String adminTokenName) {
        this.adminTokenName = adminTokenName;
    }

    public String getAppSecretKey() {
        return appSecretKey;
    }

    public void setAppSecretKey(String appSecretKey) {
        this.appSecretKey = appSecretKey;
    }

    public long getAppTtl() {
        return appTtl;
    }

    public void setAppTtl(long appTtl) {
        this.appTtl = appTtl;
    }

    public String getAppTokenName() {
        return appTokenName;
    }

    public void setAppTokenName(String appTokenName) {
        this.appTokenName = appTokenName;
    }
}

