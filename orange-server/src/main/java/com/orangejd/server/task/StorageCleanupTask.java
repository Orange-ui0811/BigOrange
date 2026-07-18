package com.orangejd.server.task;

import com.orangejd.server.mapper.StorageCleanupMapper;
import com.orangejd.server.service.impl.ManagedFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StorageCleanupTask {
    private final StorageCleanupMapper mapper;
    private final ManagedFileStorageService storage;

    public StorageCleanupTask(StorageCleanupMapper mapper, ManagedFileStorageService storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void retryFailedDeletes() {
        for (String objectKey : mapper.selectDue(50)) {
            try {
                storage.retryQueuedDelete(objectKey);
                mapper.delete(objectKey);
                log.info("存储对象重试清理成功 objectKey={}", objectKey);
            } catch (RuntimeException e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
                if (message.length() > 1000) message = message.substring(0, 1000);
                mapper.recordFailure(objectKey, message);
                log.warn("存储对象重试清理失败 objectKey={}", objectKey, e);
            }
        }
    }
}
