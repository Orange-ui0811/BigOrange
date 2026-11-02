package com.orangejd.server.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.service.BeginnerProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class BeginnerProjectTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "BEGINNER_PROJECT_PIPELINE";
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            IngestionCoordinatorService.BEGINNER_DISCOVERY,
            IngestionCoordinatorService.BEGINNER_VERIFY,
            IngestionCoordinatorService.BEGINNER_ENRICH,
            IngestionCoordinatorService.BEGINNER_WEEKLY_PICK,
            TASK_TYPE);

    private final BeginnerProjectService service;
    private final IngestionRunService runService;
    private final IngestionCoordinatorService coordinator;
    private final ObjectMapper objectMapper;

    @Value("${orange.beginner-projects.auto-refresh-enabled:false}")
    private boolean autoRefreshEnabled;

    public BeginnerProjectTask(BeginnerProjectService service,
                               IngestionRunService runService,
                               IngestionCoordinatorService coordinator,
                               ObjectMapper objectMapper) {
        this.service = service;
        this.runService = runService;
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${orange.beginner-projects.refresh-cron:0 30 4 * * 1}",
            zone = "${orange.leaderboard.refresh-zone:Asia/Shanghai}")
    public void scheduledDiscovery() {
        if (autoRefreshEnabled) {
            coordinator.scheduled(IngestionCoordinatorService.BEGINNER_DISCOVERY,
                    LocalDate.now().toString());
        }
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public boolean supports(String candidateTaskType) {
        return SUPPORTED_TASK_TYPES.contains(candidateTaskType);
    }

    @Override
    public Object execute(SystemTask task) {
        JsonNode payload = readPayload(task.getPayloadJson());
        String mode = payload.path("mode").asText();
        return switch (mode) {
            case "DISCOVERY" -> discover(task, payload.path("limit").asInt(60));
            case "VERIFY" -> verify(task, payload.path("projectId").asLong(0), payload.path("limit").asInt(50));
            case "ENRICH" -> enrich(task, payload.path("limit").asInt(40));
            case "WEEKLY_PICK" -> service.rebuildWeeklyPicks("ALGORITHM");
            default -> throw new IllegalArgumentException("Unsupported beginner project mode: " + mode);
        };
    }

    private Object discover(SystemTask task, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<String> repositories = service.discover(limit);
        if (repositories.isEmpty()) {
            String error = "GitHub discovery returned no repositories";
            runService.item(task.getRunId(), "DISCOVERY", "github-search", "FAILED", null, error);
            runService.progress(task.getRunId(), 1, 0, 1, 0, error);
            throw new IllegalStateException(error);
        }
        runService.item(task.getRunId(), "DISCOVERY", "github-search", "SUCCEEDED",
                java.util.Map.of("repositories", repositories.size()), null);
        int success = 0;
        int failed = 0;
        for (String fullName : repositories) {
            checkCancelled(task.getRunId());
            try {
                BeginnerProject project = service.inspectAndStore(fullName, task.getRunId());
                success++;
                runService.item(task.getRunId(), "SCORE", fullName, "SUCCEEDED",
                        java.util.Map.of("projectId", project.getId(), "score", project.getBeginnerScore()), null);
            } catch (Exception e) {
                failed++;
                runService.item(task.getRunId(), "SCORE", fullName, "FAILED", null, e.getMessage());
            }
            runService.progress(task.getRunId(), repositories.size(), success, failed, 0,
                    failed > 0 ? "部分仓库核验失败" : null);
        }
        int enrichLimit = Math.min(100, Math.max(8, repositories.size()));
        BeginnerProjectService.EnrichmentResult enriched = enrichPending(task, enrichLimit);
        service.rebuildWeeklyPicks("ALGORITHM");
        int finalFailed = failed + enriched.failed();
        int finalSuccess = Math.min(success, enriched.success());
        int finalSkipped = Math.max(0, repositories.size() - finalSuccess - finalFailed);
        runService.progress(task.getRunId(), repositories.size(), finalSuccess, finalFailed, finalSkipped,
                finalFailed > 0 ? "Some beginner projects failed during refresh" : null);
        if (failed > 0 || enriched.failed() > 0) {
            throw new IllegalStateException("新手项目刷新存在失败项: fetch=" + failed
                    + ", enrich=" + enriched.failed());
        }
        return java.util.Map.of("discovered", repositories.size(), "enriched", enriched.success());
    }

    private Object verify(SystemTask task, long projectId, int requestedLimit) {
        if (projectId > 0) {
            Object projectValue = service.detail(projectId, true).get("project");
            if (!(projectValue instanceof com.orangejd.server.dto.BeginnerProjectView project)) {
                throw new IllegalArgumentException("Beginner project does not exist");
            }
            service.inspectAndStore(project.fullName(), task.getRunId());
            runService.item(task.getRunId(), "VERIFY", project.fullName(), "SUCCEEDED", null, null);
            runService.progress(task.getRunId(), 1, 1, 0, 0, null);
            return java.util.Map.of("verified", 1);
        }
        // A bounded discovery pass also refreshes metadata and avoids maintaining a second GitHub scan path.
        return discover(task, Math.max(1, Math.min(requestedLimit, 100)));
    }

    private Object enrich(SystemTask task, int requestedLimit) {
        return enrichPending(task, Math.max(1, Math.min(requestedLimit, 100)));
    }

    private BeginnerProjectService.EnrichmentResult enrichPending(SystemTask task, int requestedLimit) {
        int remaining = requestedLimit;
        int success = 0;
        int failed = 0;
        java.util.List<Long> missing = new java.util.ArrayList<>();
        while (remaining > 0) {
            checkCancelled(task.getRunId());
            BeginnerProjectService.EnrichmentResult batch = service.enrichPending(
                    Math.min(8, remaining), task.getConfigVersion());
            if (batch.success() == 0 && batch.failed() == 0) break;
            success += batch.success();
            failed += batch.failed();
            missing.addAll(batch.missingIds());
            for (Long id : batch.missingIds()) {
                runService.item(task.getRunId(), "ENRICH", String.valueOf(id), "FAILED", null,
                        "AI response omitted the project");
            }
            remaining -= batch.success() + batch.failed();
            runService.progress(task.getRunId(), requestedLimit, success, failed, 0,
                    failed > 0 ? "部分项目说明生成失败" : null);
            if (batch.success() == 0) break;
        }
        return new BeginnerProjectService.EnrichmentResult(success, failed, missing);
    }

    private JsonNode readPayload(String payload) {
        try { return objectMapper.readTree(payload); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid beginner project task payload", e); }
    }

    private void checkCancelled(Long runId) {
        if (runService.cancelled(runId)) throw new IllegalStateException("任务已取消");
    }
}
