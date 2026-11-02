package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class QuotaOperation {
    private Long id;
    private String operationNo;
    private Long accountId;
    private String idempotencyScope;
    private String requestId;
    private String idempotencyKeyHash;
    private String requestHash;
    private String serviceKey;
    private Long priceVersion;
    private Long amount;
    private String status;
    private Long debitLogId;
    private Long refundLogId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationNo() { return operationNo; }
    public void setOperationNo(String operationNo) { this.operationNo = operationNo; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getIdempotencyScope() { return idempotencyScope; }
    public void setIdempotencyScope(String idempotencyScope) { this.idempotencyScope = idempotencyScope; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String idempotencyKeyHash) { this.idempotencyKeyHash = idempotencyKeyHash; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public Long getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Long priceVersion) { this.priceVersion = priceVersion; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDebitLogId() { return debitLogId; }
    public void setDebitLogId(Long debitLogId) { this.debitLogId = debitLogId; }
    public Long getRefundLogId() { return refundLogId; }
    public void setRefundLogId(Long refundLogId) { this.refundLogId = refundLogId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
