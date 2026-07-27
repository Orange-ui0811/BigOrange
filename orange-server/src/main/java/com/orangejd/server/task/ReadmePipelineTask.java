package com.orangejd.server.task;

import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.service.DocumentService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReadmePipelineTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "README_PIPELINE";

    private final DocumentService documentService;
    private final IngestionRunService runService;

    public ReadmePipelineTask(DocumentService documentService, IngestionRunService runService) {
        this.documentService = documentService;
        this.runService = runService;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Object execute(SystemTask task) {
        int success = documentService.retryPendingGithub(100, task.getConfigVersion(), task.getRunId());
        runService.progress(task.getRunId(), success, success, 0, 0, null);
        return Map.of("retried", success);
    }
}
