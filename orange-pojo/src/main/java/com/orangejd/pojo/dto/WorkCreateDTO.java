package com.orangejd.pojo.dto;

import java.math.BigDecimal;
import java.util.List;

public class WorkCreateDTO {
    private String title;
    private String summary;
    private String detail;
    private Long categoryId;
    private String tags;
    private String coverUrl;
    private String demoUrl;
    private String videoUrl;
    private List<SaleOption> saleOptions;

    public static class SaleOption {
        private String saleType;
        private BigDecimal price;
        private String description;
        public String getSaleType() { return saleType; }
        public void setSaleType(String saleType) { this.saleType = saleType; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getDemoUrl() { return demoUrl; }
    public void setDemoUrl(String demoUrl) { this.demoUrl = demoUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public List<SaleOption> getSaleOptions() { return saleOptions; }
    public void setSaleOptions(List<SaleOption> saleOptions) { this.saleOptions = saleOptions; }
}
