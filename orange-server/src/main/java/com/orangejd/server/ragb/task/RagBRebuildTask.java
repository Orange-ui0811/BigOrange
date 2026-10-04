package com.orangejd.server.ragb.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.ragb.service.RagBGenerationService;
import org.springframework.stereotype.Component;

@Component
public class RagBRebuildTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "RAG_B_REBUILD_GENERATION";

    private final ObjectMapper objectMapper;
    private final RagBGenerationService generations;

    public RagBRebuildTask(ObjectMapper objectMapper, RagBGenerationService generations) {
        this.objectMapper = objectMapper;
        this.generations = generations;
    }

    @Override
    public String taskType() { return TASK_TYPE; }

    @Override
    public Object execute(SystemTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        return generations.rebuild(payload.path("generationId").longValue());
    }
}
