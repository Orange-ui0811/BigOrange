package com.orangejd.server.ragb.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagBChunk {
    private Long id;
    private Long documentId;
    private Long versionId;
    private Integer chunkIndex;
    private String sectionPath;
    private Integer startOffset;
    private Integer endOffset;
    private String content;
    private String metadataJson;
    private String vectorKey;
    private String indexStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
