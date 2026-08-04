package com.orangejd.server.foundation.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionRunService {
    private static final int LEASE_SECONDS = 120;

    private final IngestionRunMapper mapper;
    private final ObjectMapper objectMapper;
    private final String ownerId = UUID.randomUUID().toString();

    public IngestionRunService(IngestionRunMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StartResult create(String pipelineType, String triggerSource, String businessKey,
                              String configType, String configVersion) {
        String lockKey = lockKey(pipelineType);
        Long active = mapper.selectActiveLock(lockKey);
        if (active != null) return new StartResult(active, false);

        IngestionRun run = new IngestionRun();
        run.setPipelineType(pipelineType);
        run.setTriggerSource(triggerSource);
        run.setBusinessKey(businessKey);
        run.setConfigType(configType);
        run.setConfigVersion(configVersion);
        int inserted = mapper.insertIdempotent(run);
        if (inserted == 0) {
            IngestionRun existing = mapper.selectByBusinessKey(pipelineType, businessKey);
            if (existing == null) throw new IllegalStateException("幂等运行记录创建失败");
            return new StartResult(existing.getId(), false);
        }
        mapper.acquireLock(lockKey, run.getId(), ownerId, LEASE_SECONDS);
        Long acquired = mapper.selectActiveLock(lockKey);
        boolean started = run.getId().equals(acquired);
        if (!started) {
            mapper.markSuperseded(run.getId(), "已有同类抓取任务正在运行: " + acquired);
        }
        return new StartResult(acquired, started);
    }

    public IngestionRun get(Long id) {
        return mapper.selectById(id);
    }

    public Map<String, Object> detail(Long id) {
        IngestionRun run = mapper.selectById(id);
        if (run == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run);
        result.put("items", mapper.selectItems(id));
        return result;
    }

    public Map<String, Object> page(String pipelineType, String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        List<IngestionRun> records = mapper.selectPage(pipelineType, status,
                (safePage - 1) * safeSize, safeSize);
        return Map.of("records", records, "total", mapper.count(pipelineType, status));
    }

    public void running(Long runId) {
        if (runId != null) mapper.markRunning(runId);
    }

    public void heartbeat(Long runId) {
        if (runId != null) mapper.heartbeatLock(runId, LEASE_SECONDS);
    }

    public void deferLease(Long runId, long retryDelaySeconds) {
        if (runId != null) {
            mapper.heartbeatLock(runId, (int) Math.min(Integer.MAX_VALUE,
                    Math.max(LEASE_SECONDS, retryDelaySeconds + LEASE_SECONDS)));
        }
    }

    public boolean cancelled(Long runId) {
        if (runId == null) return false;
        IngestionRun run = mapper.selectById(runId);
        return run != null && run.isCancelRequested();
    }

    public void item(Long runId, String stage, String itemKey, String status, Object result, String error) {
        if (runId == null) return;
        mapper.upsertItem(runId, stage, itemKey, status, json(result), abbreviate(error));
    }

    public List<String> succeededItemKeys(Long runId, String stage) {
        if (runId == null) return List.of();
        return mapper.selectSucceededItemKeys(runId, stage);
    }

    public void progress(Long runId, int total, int success, int failed, int skipped, String error) {
        if (runId == null) return;
        int done = success + failed + skipped;
        int progress = total <= 0 ? 0 : Math.min(99, done * 100 / total);
        mapper.updateProgress(runId, "RUNNING",
                progress, total, success, failed, skipped, abbreviate(error));
    }

    public void complete(Long runId, boolean success, String error) {
        if (runId == null) return;
        IngestionRun run = mapper.selectById(runId);
        if (run == null) return;
        String status;
        if (run.isCancelRequested()) status = "CANCELLED";
        else if (success && run.getFailedCount() == 0) status = "SUCCEEDED";
        else if (run.getSuccessCount() > 0) status = "PARTIAL_FAILED";
        else status = "FAILED";
        mapper.updateProgress(runId, status, 100, run.getTotalCount(), run.getSuccessCount(),
                run.getFailedCount(), run.getSkippedCount(), abbreviate(error));
        mapper.releaseLock(runId);
    }

    public boolean cancel(Long runId, boolean releaseLock) {
        boolean changed = mapper.requestCancel(runId) == 1;
        if (changed && releaseLock) mapper.releaseLock(runId);
        return changed;
    }

    private String lockKey(String pipelineType) {
        if (pipelineType.startsWith("LEADERBOARD")) return "LEADERBOARD_FULL";
        if (pipelineType.startsWith("ISSUE_VERIFY")) return "ISSUE_VERIFY";
        if (pipelineType.startsWith("ISSUE")) return "ISSUE_FULL";
        if (pipelineType.startsWith("README")) return "README_PIPELINE";
        if (pipelineType.startsWith("BEGINNER")) return "BEGINNER_PROJECT_PIPELINE";
        return pipelineType;
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("运行结果不能序列化", ex);
        }
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 1900 ? value : value.substring(0, 1900);
    }

    public record StartResult(Long runId, boolean started) {
    }
}
