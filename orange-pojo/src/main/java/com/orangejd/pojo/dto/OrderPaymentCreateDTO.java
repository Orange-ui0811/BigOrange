package com.orangejd.pojo.dto;

public class OrderPaymentCreateDTO {
    private Long orderId;
    private String stageType;
    private String method;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getStageType() { return stageType; }
    public void setStageType(String stageType) { this.stageType = stageType; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
