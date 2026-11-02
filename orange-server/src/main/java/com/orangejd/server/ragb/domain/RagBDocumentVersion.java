package com.orangejd.server.ragb.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagBDocumentVersion {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String contentHash;
    private String originalContent;
    private String translatedContent;
    private String status;
    private Integer chunkCount;
    private Long indexedGenerationId;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
