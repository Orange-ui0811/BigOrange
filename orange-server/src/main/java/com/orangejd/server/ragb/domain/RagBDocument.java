package com.orangejd.server.ragb.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagBDocument {
    private Long id;
    private String sourceType;
    private String sourceRef;
    private String title;
    private String status;
    private Long activeVersionId;
    private String contentHash;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime deletedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
