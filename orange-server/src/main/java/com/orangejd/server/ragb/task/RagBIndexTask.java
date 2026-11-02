package com.orangejd.server.ragb.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.ragb.service.RagBIndexingService;
import org.springframework.stereotype.Component;

@Component
public class RagBIndexTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "RAG_B_INDEX_DOCUMENT";

    private final ObjectMapper objectMapper;
    private final RagBIndexingService indexingService;

    public RagBIndexTask(ObjectMapper objectMapper, RagBIndexingService indexingService) {
        this.objectMapper = objectMapper;
        this.indexingService = indexingService;
    }

    @Override
    public String taskType() { return TASK_TYPE; }

    @Override
    public Object execute(SystemTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        return indexingService.index(payload.path("documentId").longValue(), payload.path("versionId").longValue());
    }
}
