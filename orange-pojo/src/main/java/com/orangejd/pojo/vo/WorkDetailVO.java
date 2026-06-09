package com.orangejd.pojo.vo;

import java.math.BigDecimal;
import java.util.List;

public class WorkDetailVO extends WorkVO {
    private List<SaleOptionVO> saleOptions;

    public static class SaleOptionVO {
        private Long id;
        private String saleType;
        private BigDecimal price;
        private String description;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSaleType() { return saleType; }
        public void setSaleType(String saleType) { this.saleType = saleType; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public List<SaleOptionVO> getSaleOptions() { return saleOptions; }
    public void setSaleOptions(List<SaleOptionVO> saleOptions) { this.saleOptions = saleOptions; }
}
