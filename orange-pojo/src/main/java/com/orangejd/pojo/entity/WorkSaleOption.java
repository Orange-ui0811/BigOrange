package com.orangejd.pojo.entity;

public class WorkSaleOption extends BaseEntity {
    private Long workId;
    private String saleType;
    private java.math.BigDecimal price;
    private String description;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getSaleType() { return saleType; }
    public void setSaleType(String saleType) { this.saleType = saleType; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
