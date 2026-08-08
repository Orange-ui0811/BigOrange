package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class QuotaLog {
    private Long id;
    private String transactionNo;
    private Long operationId;
    private Long originalLogId;
    private Long accountId;
    private String type;
    private Long amount;
    private Long balance;
    private String source;
    private String serviceKey;
    private Long priceVersion;
    private String description;
    private String requestId;
    private String requestHash;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }
    public Long getOperationId() { return operationId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }
    public Long getOriginalLogId() { return originalLogId; }
    public void setOriginalLogId(Long originalLogId) { this.originalLogId = originalLogId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public Long getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Long priceVersion) { this.priceVersion = priceVersion; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
