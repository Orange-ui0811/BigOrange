package com.orangejd.pojo.entity;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 * <p>
 * 不继承 BaseEntity——与 TrendHistory 风格一致，自声明 id 和 createTime。
 * 来源：MANUAL（手动上传）/ GITHUB（榜单刷新自动采集）。
 */
public class KnowledgeDocument {

    private Long id;
    private String title;
    private String source;       // MANUAL / GITHUB
    private String sourceRef;    // GitHub full_name 或文件路径（幂等键）
    private Integer chunkCount;
    private Long fileSize;
    private String content;
    private String originalContent;
    private String fetchStatus;
    private String translationStatus;
    private String storageStatus;
    private String vectorStatus;
    private String pipelineError;
    private LocalDateTime createTime;

    // ---- getters / setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
    public String getFetchStatus() { return fetchStatus; }
    public void setFetchStatus(String fetchStatus) { this.fetchStatus = fetchStatus; }
    public String getTranslationStatus() { return translationStatus; }
    public void setTranslationStatus(String translationStatus) { this.translationStatus = translationStatus; }
    public String getStorageStatus() { return storageStatus; }
    public void setStorageStatus(String storageStatus) { this.storageStatus = storageStatus; }
    public String getVectorStatus() { return vectorStatus; }
    public void setVectorStatus(String vectorStatus) { this.vectorStatus = vectorStatus; }
    public String getPipelineError() { return pipelineError; }
    public void setPipelineError(String pipelineError) { this.pipelineError = pipelineError; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
