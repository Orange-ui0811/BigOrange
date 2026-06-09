package com.orangejd.pojo.dto;

import java.math.BigDecimal;

public class OrderCreateDTO {
    private String sourceType;  // QUOTE / WORK
    private Long sourceId;       // quote.id 或 work.id
    private BigDecimal deposit;
    private BigDecimal finalAmount;
    private BigDecimal totalAmount;

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public BigDecimal getDeposit() { return deposit; }
    public void setDeposit(BigDecimal deposit) { this.deposit = deposit; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
