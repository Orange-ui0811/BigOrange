package com.orangejd.server.task;

import com.orangejd.server.service.StorageCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StorageCleanupTask {
    private final StorageCleanupService cleanupService;

    public StorageCleanupTask(StorageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void retryFailedDeletes() {
        cleanupService.retryFailedDeletes();
    }
}
