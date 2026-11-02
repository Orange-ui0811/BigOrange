package com.orangejd.server.ragb.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagBIndexGeneration {
    private Long id;
    private String generationKey;
    private String redisPrefix;
    private String adapterType;
    private String embeddingModel;
    private String embeddingProvider;
    private String embeddingRevision;
    private Integer embeddingDimensions;
    private String chunkPolicy;
    private String status;
    private Boolean active;
    private Integer documentCount;
    private Integer chunkCount;
    private String lastError;
    private LocalDateTime activatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
