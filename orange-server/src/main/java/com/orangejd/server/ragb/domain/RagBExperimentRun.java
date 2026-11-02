package com.orangejd.server.ragb.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagBExperimentRun {
    private Long id;
    private String runKey;
    private String mode;
    private String datasetVersion;
    private String modelSnapshot;
    private String retrievalSnapshot;
    private String status;
    private String resultJson;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
