package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

/**
 * 定价变更审计日志表
 */
public class PricingChangeLog {
    private Long id;
    private String serviceKey;
    private Integer oldCost;
    private Integer newCost;
    private Long adminId;
    private LocalDateTime changedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }

    public Integer getOldCost() { return oldCost; }
    public void setOldCost(Integer oldCost) { this.oldCost = oldCost; }

    public Integer getNewCost() { return newCost; }
    public void setNewCost(Integer newCost) { this.newCost = newCost; }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
