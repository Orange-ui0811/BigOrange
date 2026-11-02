package com.orangejd.pojo.vo;

public class DeliveryAttachmentVO {
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long size;

    public DeliveryAttachmentVO() {}

    public DeliveryAttachmentVO(String objectKey, String originalName, String contentType, Long size) {
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
    }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}
