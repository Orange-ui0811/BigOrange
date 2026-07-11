package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

/**
 * AI 服务消费定价表
 */
public class AiServicePricing {
    private String serviceKey;      // 主键，如 ai:trend:v1
    private String serviceName;     // 显示名，如 "趋势分析 V1"
    private Integer segmentCost;    // 消费金额（segments）
    private Long updatedBy;         // 最后修改人 adminId
    private LocalDateTime updatedAt;

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Integer getSegmentCost() { return segmentCost; }
    public void setSegmentCost(Integer segmentCost) { this.segmentCost = segmentCost; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
