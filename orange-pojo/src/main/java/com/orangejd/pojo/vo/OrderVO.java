package com.orangejd.pojo.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderVO {
    private Long id;
    private String sourceType;
    private Long sourceId;
    private Long buyerId;
    private String buyerName;
    private Long sellerId;
    private String sellerName;
    private Long workId;
    private String workTitle;
    private BigDecimal deposit;
    private BigDecimal finalAmount;
    private BigDecimal totalAmount;
    private String orderStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<PaymentStageVO> paymentStages;
    private List<DeliveryVO> deliveries;

    public static class PaymentStageVO {
        private Long id;
        private String stageType;
        private BigDecimal amount;
        private String payStatus;
        private LocalDateTime payTime;
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getStageType() { return stageType; } public void setStageType(String stageType) { this.stageType = stageType; }
        public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getPayStatus() { return payStatus; } public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
        public LocalDateTime getPayTime() { return payTime; } public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    }
    public static class DeliveryVO {
        private Long id;
        private Long delivererId;
        private String content;
        private String attachmentUrl;
        private LocalDateTime createTime;
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public Long getDelivererId() { return delivererId; } public void setDelivererId(Long delivererId) { this.delivererId = delivererId; }
        public String getContent() { return content; } public void setContent(String content) { this.content = content; }
        public String getAttachmentUrl() { return attachmentUrl; } public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
        public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    // Getters and setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; } public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; } public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getBuyerId() { return buyerId; } public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public String getBuyerName() { return buyerName; } public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public Long getSellerId() { return sellerId; } public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; } public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public Long getWorkId() { return workId; } public void setWorkId(Long workId) { this.workId = workId; }
    public String getWorkTitle() { return workTitle; } public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public BigDecimal getDeposit() { return deposit; } public void setDeposit(BigDecimal deposit) { this.deposit = deposit; }
    public BigDecimal getFinalAmount() { return finalAmount; } public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getOrderStatus() { return orderStatus; } public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public List<PaymentStageVO> getPaymentStages() { return paymentStages; } public void setPaymentStages(List<PaymentStageVO> paymentStages) { this.paymentStages = paymentStages; }
    public List<DeliveryVO> getDeliveries() { return deliveries; } public void setDeliveries(List<DeliveryVO> deliveries) { this.deliveries = deliveries; }
}
