package com.orangejd.server.task;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OpenSourceRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(OpenSourceRefreshTask.class);

    private final OpenSourceMapper mapper;
    private final OpenSourceGithubService githubService;
    private final OpenSourceTranslationService translationService;
    private final AtomicBoolean fullRunning = new AtomicBoolean(false);
    private final AtomicBoolean verifyRunning = new AtomicBoolean(false);
    private volatile LocalDateTime lastFullStart;
    private volatile LocalDateTime lastFullFinish;
    private volatile String lastFullError;
    private volatile int lastSaved;
    private volatile int lastTranslated;

    public OpenSourceRefreshTask(OpenSourceMapper mapper, OpenSourceGithubService githubService,
                                 OpenSourceTranslationService translationService) {
        this.mapper = mapper;
        this.githubService = githubService;
        this.translationService = translationService;
    }

    @Scheduled(cron = "${orange.open-source.full-refresh-cron:0 20 3 */2 * *}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledFullRefresh() {
        runFullRefresh();
    }

    @Scheduled(cron = "${orange.open-source.verify-cron:0 0 */12 * * *}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledVerification() {
        runLightVerification();
    }

    public boolean triggerFullRefresh() {
        if (fullRunning.get()) return false;
        CompletableFuture.runAsync(this::runFullRefresh);
        return true;
    }

    public boolean triggerVerification() {
        if (verifyRunning.get()) return false;
        CompletableFuture.runAsync(this::runLightVerification);
        return true;
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
            List<Map<String, Object>> repositories = mapper.selectEnabledRepositories();
            for (Map<String, Object> repository : repositories) {
                try {
                    lastSaved += githubService.refreshRepository(((Number) repository.get("id")).longValue());
                } catch (Exception e) {
                    log.warn("开源仓库刷新失败 repo={}, reason={}", repository.get("fullName"), e.getMessage());
                }
                sleep(1_200);
            }
            lastTranslated = translationService.translatePending(80);
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
}
