package com.orangejd.pojo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderPayment {
    private Long id;
    private String tradeNo;
    private Long orderId;
    private Long paymentStageId;
    private Long payerAccountId;
    private String stageType;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentProvider;
    private String providerTradeNo;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPaymentStageId() { return paymentStageId; }
    public void setPaymentStageId(Long paymentStageId) { this.paymentStageId = paymentStageId; }
    public Long getPayerAccountId() { return payerAccountId; }
    public void setPayerAccountId(Long payerAccountId) { this.payerAccountId = payerAccountId; }
    public String getStageType() { return stageType; }
    public void setStageType(String stageType) { this.stageType = stageType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentProvider() { return paymentProvider; }
    public void setPaymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; }
    public String getProviderTradeNo() { return providerTradeNo; }
    public void setProviderTradeNo(String providerTradeNo) { this.providerTradeNo = providerTradeNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
}
