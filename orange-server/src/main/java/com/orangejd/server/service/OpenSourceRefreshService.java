
package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.mapper.OpenSourceMapper;
import com.orangejd.server.service.OpenSourceGithubService;
import com.orangejd.server.service.OpenSourceTranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Service
public class OpenSourceRefreshService {
    public static final String TASK_TYPE = "OPEN_SOURCE_REFRESH";

    private static final Logger log = LoggerFactory.getLogger(OpenSourceRefreshService.class);

    private final OpenSourceMapper mapper;
    private final OpenSourceGithubService githubService;
    private final OpenSourceTranslationService translationService;
    private final SystemTaskService taskService;
    private final ObjectMapper objectMapper;
    private final IngestionCoordinatorService ingestionCoordinator;
    private final IngestionRunService ingestionRunService;
    private final AtomicBoolean fullRunning = new AtomicBoolean(false);
    private final AtomicBoolean verifyRunning = new AtomicBoolean(false);
    private volatile LocalDateTime lastFullStart;
    private volatile LocalDateTime lastFullFinish;
    private volatile String lastFullError;
    private volatile int lastSaved;
    private volatile int lastTranslated;
    @Value("${orange.ingestion.pipeline-v2-enabled:false}")
    private boolean pipelineV2Enabled;

    public OpenSourceRefreshService(OpenSourceMapper mapper,
                                 OpenSourceGithubService githubService,
                                 OpenSourceTranslationService translationService,
                                 SystemTaskService taskService,
                                 ObjectMapper objectMapper,
                                 IngestionCoordinatorService ingestionCoordinator,
                                 IngestionRunService ingestionRunService) {
        this.mapper = mapper;
        this.githubService = githubService;
        this.translationService = translationService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.ingestionCoordinator = ingestionCoordinator;
        this.ingestionRunService = ingestionRunService;
    }

    public void scheduledFullRefresh() {
        if (pipelineV2Enabled) {
            ingestionCoordinator.scheduled(IngestionCoordinatorService.ISSUE_FULL,
                    java.time.LocalDate.now().toString());
        } else {
            enqueue("FULL", "scheduled-full:" + java.time.LocalDate.now(), 8);
        }
    }

    public void scheduledVerification() {
        String bucket = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        if (pipelineV2Enabled) {
            ingestionCoordinator.scheduled(IngestionCoordinatorService.ISSUE_VERIFY, bucket);
        } else {
            enqueue("VERIFY", "scheduled-verify:" + bucket, 5);
        }
    }

    public boolean triggerFullRefresh() {
        if (pipelineV2Enabled) {
            return ingestionCoordinator.start(IngestionCoordinatorService.ISSUE_FULL,
                    "LEGACY_API", null, Map.of()).started();
        }
        if (fullRunning.get()) return false;
        enqueue("FULL", "manual-full:" + UUID.randomUUID(), 8);
        return true;
    }

    public boolean triggerVerification() {
        if (pipelineV2Enabled) {
            return ingestionCoordinator.start(IngestionCoordinatorService.ISSUE_VERIFY,
                    "LEGACY_API", null, Map.of()).started();
        }
        if (verifyRunning.get()) return false;
        enqueue("VERIFY", "manual-verify:" + UUID.randomUUID(), 5);
        return true;
    }

    public boolean triggerTranslation() {
        if (pipelineV2Enabled) {
            return ingestionCoordinator.start(IngestionCoordinatorService.ISSUE_TRANSLATE,
                    "LEGACY_API", null, Map.of()).started();
        }
        enqueue("TRANSLATE", "manual-translate:" + UUID.randomUUID(), 5);
        return true;
    }

    public String taskType() {
        return TASK_TYPE;
    }

    public Object execute(SystemTask task) throws Exception {
        String mode = objectMapper.readTree(task.getPayloadJson()).path("mode").asText();
        if ("FULL".equals(mode)) {
            runFullRefresh(task);
            if (lastFullError != null) {
                throw new IllegalStateException("Open source refresh failed: " + lastFullError);
            }
        } else if ("TRANSLATE".equals(mode)) {
            lastTranslated = translationService.translatePending(
                    100, task.getRunId(), task.getConfigVersion());
            ingestionRunService.progress(task.getRunId(), lastTranslated, lastTranslated,
                    0, 0, null);
        } else if ("VERIFY".equals(mode)) {
            runLightVerification(task);
        } else {
            throw new IllegalArgumentException("Unsupported open source refresh mode: " + mode);
        }
        return status();
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("fullRunning", fullRunning.get());
        status.put("verifyRunning", verifyRunning.get());
        status.put("lastFullStart", lastFullStart);
        status.put("lastFullFinish", lastFullFinish);
        status.put("lastFullError", lastFullError);
        status.put("lastSaved", lastSaved);
        status.put("lastTranslated", lastTranslated);
        return status;
    }

    private void runFullRefresh(SystemTask task) {
        if (!fullRunning.compareAndSet(false, true)) return;
        lastFullStart = LocalDateTime.now();
        lastFullError = null;
        lastSaved = 0;
        lastTranslated = 0;
        int failed = 0;
        try {
            // Repair cached issues first so translation is not blocked by a long GitHub crawl.
            try {
                lastTranslated += translationService.translatePending(
                        80, task.getRunId(), task.getConfigVersion());
            } catch (Exception ex) {
                failed++;
                lastFullError = ex.getMessage();
            }
            List<Map<String, Object>> repositories = mapper.selectEnabledRepositories();
            int total = repositories.size() + 2;
            int succeeded = lastTranslated > 0 ? 1 : 0;
            for (Map<String, Object> repository : repositories) {
                if (ingestionRunService.cancelled(task.getRunId())) {
                    throw new IllegalStateException("任务已取消");
                }
                try {
                    int saved = githubService.refreshRepository(((Number) repository.get("id")).longValue());
                    lastSaved += saved;
                    succeeded++;
                    ingestionRunService.item(task.getRunId(), "FETCH",
                            String.valueOf(repository.get("fullName")), "SUCCEEDED",
                            Map.of("saved", saved), null);
                } catch (Exception e) {
                    failed++;
                    ingestionRunService.item(task.getRunId(), "FETCH",
                            String.valueOf(repository.get("fullName")), "FAILED", null, e.getMessage());
                    log.warn("开源仓库刷新失败 repo={}, reason={}", repository.get("fullName"), e.getMessage());
                }
                ingestionRunService.progress(task.getRunId(), total, succeeded, failed,
                        0, failed > 0 ? "部分仓库同步失败" : null);
                sleep(1_200);
            }
            try {
                int translated = translationService.translatePending(
                        80, task.getRunId(), task.getConfigVersion());
                lastTranslated += translated;
                succeeded++;
            } catch (Exception ex) {
                failed++;
                lastFullError = ex.getMessage();
            }
            ingestionRunService.progress(task.getRunId(), total, succeeded, failed, 0, lastFullError);
            lastFullFinish = LocalDateTime.now();
            log.info("开源 Issue 完整刷新完成 saved={}, translated={}", lastSaved, lastTranslated);
            if (failed > 0) throw new IllegalStateException("开源刷新存在失败项: " + failed);
        } catch (Exception e) {
            lastFullError = e.getMessage();
            log.error("开源 Issue 完整刷新失败", e);
        } finally {
            fullRunning.set(false);
        }
    }

    private void runLightVerification(SystemTask task) {
        if (!verifyRunning.compareAndSet(false, true)) return;
        try {
            if (fullRunning.get()) return;
            List<Map<String, Object>> issues = mapper.selectPublishedForVerification(80);
            int success = 0;
            int failed = 0;
            for (Map<String, Object> issue : issues) {
                if (ingestionRunService.cancelled(task.getRunId())) {
                    throw new IllegalStateException("任务已取消");
                }
                try {
                    githubService.verifyIssue(((Number) issue.get("id")).longValue());
                    success++;
                    ingestionRunService.item(task.getRunId(), "VERIFY",
                            String.valueOf(issue.get("id")), "SUCCEEDED", null, null);
                } catch (Exception e) {
                    failed++;
                    ingestionRunService.item(task.getRunId(), "VERIFY",
                            String.valueOf(issue.get("id")), "FAILED", null, e.getMessage());
                    log.debug("Issue 核验失败 id={}: {}", issue.get("id"), e.getMessage());
                }
                ingestionRunService.progress(task.getRunId(), issues.size(), success, failed,
                        0, failed > 0 ? "部分 Issue 核验失败" : null);
                sleep(700);
            }
            if (failed > 0) throw new IllegalStateException("Issue 核验存在失败项: " + failed);
        } finally {
            verifyRunning.set(false);
        }
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void enqueue(String mode, String businessKey, int maxAttempts) {
        taskService.enqueue(TASK_TYPE, businessKey, Map.of("mode", mode), maxAttempts);
    }
}

