package com.orangejd.server.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.mapper.OpenSourceMapper;
import com.orangejd.server.service.OpenSourceGithubService;
import com.orangejd.server.service.OpenSourceTranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Component
public class OpenSourceRefreshTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "OPEN_SOURCE_REFRESH";

    private static final Logger log = LoggerFactory.getLogger(OpenSourceRefreshTask.class);

    private final OpenSourceMapper mapper;
    private final OpenSourceGithubService githubService;
    private final OpenSourceTranslationService translationService;
    private final SystemTaskService taskService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean fullRunning = new AtomicBoolean(false);
    private final AtomicBoolean verifyRunning = new AtomicBoolean(false);
    private volatile LocalDateTime lastFullStart;
    private volatile LocalDateTime lastFullFinish;
    private volatile String lastFullError;
    private volatile int lastSaved;
    private volatile int lastTranslated;

    public OpenSourceRefreshTask(OpenSourceMapper mapper,
                                 OpenSourceGithubService githubService,
                                 OpenSourceTranslationService translationService,
                                 SystemTaskService taskService,
                                 ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.githubService = githubService;
        this.translationService = translationService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${orange.open-source.full-refresh-cron:0 20 3 */2 * *}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledFullRefresh() {
        enqueue("FULL", "scheduled-full:" + java.time.LocalDate.now(), 8);
    }

    @Scheduled(cron = "${orange.open-source.verify-cron:0 0 */12 * * *}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledVerification() {
        String bucket = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        enqueue("VERIFY", "scheduled-verify:" + bucket, 5);
    }

    public boolean triggerFullRefresh() {
        if (fullRunning.get()) return false;
        enqueue("FULL", "manual-full:" + UUID.randomUUID(), 8);
        return true;
    }

    public boolean triggerVerification() {
        if (verifyRunning.get()) return false;
        enqueue("VERIFY", "manual-verify:" + UUID.randomUUID(), 5);
        return true;
    }

    public boolean triggerTranslation() {
        enqueue("TRANSLATE", "manual-translate:" + UUID.randomUUID(), 5);
        return true;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Object execute(SystemTask task) throws Exception {
        String mode = objectMapper.readTree(task.getPayloadJson()).path("mode").asText();
        if ("FULL".equals(mode)) {
            runFullRefresh();
            if (lastFullError != null) {
                throw new IllegalStateException("Open source refresh failed: " + lastFullError);
            }
        } else if ("TRANSLATE".equals(mode)) {
            lastTranslated = translationService.translatePending(100);
        } else if ("VERIFY".equals(mode)) {
            runLightVerification();
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

    private void runFullRefresh() {
        if (!fullRunning.compareAndSet(false, true)) return;
        lastFullStart = LocalDateTime.now();
        lastFullError = null;
        lastSaved = 0;
        lastTranslated = 0;
        try {
            // Repair cached issues first so translation is not blocked by a long GitHub crawl.
            lastTranslated += translationService.translatePending(80);
            List<Map<String, Object>> repositories = mapper.selectEnabledRepositories();
            for (Map<String, Object> repository : repositories) {
                try {
                    lastSaved += githubService.refreshRepository(((Number) repository.get("id")).longValue());
                } catch (Exception e) {
                    log.warn("开源仓库刷新失败 repo={}, reason={}", repository.get("fullName"), e.getMessage());
                }
                sleep(1_200);
            }
            lastTranslated += translationService.translatePending(80);
            lastFullFinish = LocalDateTime.now();
            log.info("开源 Issue 完整刷新完成 saved={}, translated={}", lastSaved, lastTranslated);
        } catch (Exception e) {
            lastFullError = e.getMessage();
            log.error("开源 Issue 完整刷新失败", e);
        } finally {
            fullRunning.set(false);
        }
    }

    private void runLightVerification() {
        if (!verifyRunning.compareAndSet(false, true)) return;
        try {
            if (fullRunning.get()) return;
            for (Map<String, Object> issue : mapper.selectPublishedForVerification(80)) {
                try { githubService.verifyIssue(((Number) issue.get("id")).longValue()); }
                catch (Exception e) { log.debug("Issue 核验失败 id={}: {}", issue.get("id"), e.getMessage()); }
                sleep(700);
            }
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
