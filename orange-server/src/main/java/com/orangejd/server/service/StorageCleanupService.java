package com.orangejd.server.service;
import com.orangejd.server.mapper.StorageCleanupMapper;
import com.orangejd.server.service.impl.ManagedFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class StorageCleanupService {
    private final StorageCleanupMapper mapper; private final ManagedFileStorageService storage;
    public StorageCleanupService(StorageCleanupMapper mapper, ManagedFileStorageService storage) { this.mapper = mapper; this.storage = storage; }
    public void retryFailedDeletes() {
        for (String key : mapper.selectDue(50)) try { storage.retryQueuedDelete(key); mapper.delete(key); log.info("存储对象重试清理成功 objectKey={}", key); }
        catch (RuntimeException error) { String message=error.getMessage(); if(message==null||message.isBlank())message=error.getClass().getSimpleName(); if(message.length()>1000)message=message.substring(0,1000); mapper.recordFailure(key,message); log.warn("存储对象重试清理失败 objectKey={}",key,error); }
    }
}
