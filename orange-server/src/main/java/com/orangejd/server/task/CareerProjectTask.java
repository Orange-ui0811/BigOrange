package com.orangejd.server.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.service.CareerProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class CareerProjectTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "CAREER_PROJECT_PIPELINE";

    private final CareerProjectService service;
    private final IngestionRunService runService;
    private final IngestionCoordinatorService coordinator;
    private final ObjectMapper objectMapper;

    @Value("${orange.career-projects.auto-refresh-enabled:false}")
    private boolean autoRefreshEnabled;

    public CareerProjectTask(CareerProjectService service,
                             IngestionRunService runService,
                             IngestionCoordinatorService coordinator,
                             ObjectMapper objectMapper) {
        this.service = service;
        this.runService = runService;
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${orange.career-projects.refresh-cron:0 0 5 * * 1}",
            zone = "${orange.leaderboard.refresh-zone:Asia/Shanghai}")
    public void scheduledDiscovery() {
        if (autoRefreshEnabled) {
            coordinator.scheduled(IngestionCoordinatorService.CAREER_DISCOVERY, LocalDate.now().toString());
        }
    }

    @Override
    public String taskType() { return TASK_TYPE; }

    @Override
    public boolean supports(String value) {
        return TASK_TYPE.equals(value) || value != null && value.startsWith("CAREER_");
    }

    @Override
    public Object execute(SystemTask task) {
        JsonNode payload = payload(task.getPayloadJson());
        return switch (payload.path("mode").asText()) {
            case "DISCOVERY" -> discover(task, payload.path("limit").asInt(60));
            case "VERIFY" -> verify(task, payload.path("projectId").asLong(0));
            case "ENRICH" -> enrich(task, payload.path("limit").asInt(40));
            case "WEEKLY_PICK" -> weeklyPick(task);
            default -> throw new IllegalArgumentException("Unsupported career project mode");
        };
    }

    private Object discover(SystemTask task, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 120));
        List<String> completedRepositories = runService.succeededItemKeys(task.getRunId(), "CAREER_SCORE");
        List<String> repositories = completedRepositories.isEmpty()
                ? service.discover(limit) : completedRepositories;
        if (repositories.isEmpty()) throw new IllegalStateException("GitHub career discovery returned no repositories");
        int success = completedRepositories.size();
        int failed = 0;
        for (String fullName : repositories) {
            if (completedRepositories.contains(fullName)) continue;
            checkCancelled(task.getRunId());
            try {
                CareerProjectProfile project = service.inspectAndStore(fullName, task.getRunId());
                success++;
                runService.item(task.getRunId(), "CAREER_SCORE", fullName, "SUCCEEDED",
                        Map.of("projectId", project.getProjectId(), "score", project.getOverallScore()), null);
            } catch (Exception e) {
                failed++;
                runService.item(task.getRunId(), "CAREER_SCORE", fullName, "FAILED", null, e.getMessage());
            }
            runService.progress(task.getRunId(), repositories.size(), success, failed, 0,
                    failed > 0 ? "部分职业项目核验失败" : null);
        }
        CareerProjectService.EnrichmentResult enriched = enrichPending(task, Math.min(100, Math.max(8, repositories.size())));
        service.rebuildWeeklyPicks("ALGORITHM");
        int finalFailed = failed + enriched.failed();
        int finalSuccess = success + enriched.success();
        int totalWork = repositories.size() + enriched.success() + enriched.failed();
        runService.progress(task.getRunId(), totalWork, finalSuccess, finalFailed, 0,
                finalFailed > 0 ? "部分职业项目刷新失败" : null);
        if (finalFailed > 0) throw new IllegalStateException("Career refresh has failed items: " + finalFailed);
        return Map.of("discovered", repositories.size(), "enriched", enriched.success());
    }

    private Object verify(SystemTask task, long projectId) {
        if (projectId <= 0) return discover(task, 30);
        CareerProjectProfile project = service.inspectAndStore(service.detail(projectId, true).fullName(), task.getRunId());
        runService.item(task.getRunId(), "CAREER_VERIFY", project.getFullName(), "SUCCEEDED", null, null);
        runService.progress(task.getRunId(), 1, 1, 0, 0, null);
        return Map.of("verified", 1);
    }

    private Object enrich(SystemTask task, int limit) {
        CareerProjectService.EnrichmentResult result = enrichPending(task, Math.max(1, Math.min(limit, 100)));
        int total = result.success() + result.failed();
        runService.progress(task.getRunId(), total, result.success(), result.failed(), 0,
                result.failed() > 0 ? "部分职业项目说明生成失败" : null);
        return result;
    }

    private Object weeklyPick(SystemTask task) {
        Object result = service.rebuildWeeklyPicks("ALGORITHM");
        runService.progress(task.getRunId(), 1, 1, 0, 0, null);
        return result;
    }

    private CareerProjectService.EnrichmentResult enrichPending(SystemTask task, int requestedLimit) {
        int remaining = requestedLimit;
        int success = 0;
        int failed = 0;
        List<Long> missing = new java.util.ArrayList<>();
        while (remaining > 0) {
            checkCancelled(task.getRunId());
            CareerProjectService.EnrichmentResult batch = service.enrichPending(Math.min(8, remaining), task.getConfigVersion());
            if (batch.success() == 0 && batch.failed() == 0) break;
            success += batch.success();
            failed += batch.failed();
            missing.addAll(batch.missingIds());
            for (Long id : batch.missingIds()) {
                runService.item(task.getRunId(), "CAREER_ENRICH", String.valueOf(id), "FAILED", null,
                        "AI response omitted the project");
            }
            remaining -= batch.success() + batch.failed();
            if (batch.success() == 0) break;
        }
        return new CareerProjectService.EnrichmentResult(success, failed, missing);
    }

    private JsonNode payload(String value) {
        try { return objectMapper.readTree(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid career project task payload", e); }
    }

    private void checkCancelled(Long runId) {
        if (runService.cancelled(runId)) throw new IllegalStateException("任务已取消");
    }
}
