package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

public class Delivery {
    private Long id;
    private Long orderId;
    private Long delivererId;
    private String content;
    private String attachmentUrl;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getDelivererId() { return delivererId; }
    public void setDelivererId(Long delivererId) { this.delivererId = delivererId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
