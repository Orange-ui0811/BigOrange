package com.orangejd.pojo.vo;

/**
 * 知识库文档视图对象（列表 + 上传返回）
 */
public class KnowledgeDocumentVO {

    private Long id;
    private String title;
    private String source;       // MANUAL / GITHUB
    private String sourceRef;    // GitHub full_name 或文件路径
    private int chunkCount;
    private Long fileSize;
    private String period;       // all=永久 / week=30天后清理
    private String createTime;

    // ---- getters / setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
