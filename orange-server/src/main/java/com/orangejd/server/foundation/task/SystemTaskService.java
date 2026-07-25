package com.orangejd.server.foundation.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class SystemTaskService {
    private static final Logger log = LoggerFactory.getLogger(SystemTaskService.class);
    private static final int DISPATCH_BATCH = 20;

    private final SystemTaskMapper mapper;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final ObjectProvider<SystemTaskHandler> handlers;
    private final String workerId;

    public SystemTaskService(SystemTaskMapper mapper,
                             ObjectMapper objectMapper,
                             @Qualifier("orangeTaskExecutor") Executor executor,
                             ObjectProvider<SystemTaskHandler> handlers) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.handlers = handlers;
        this.workerId = resolveWorkerId();
    }

    public Long enqueue(String taskType, String businessKey, Object payload, int maxAttempts) {
        SystemTask task = new SystemTask();
        task.setTaskType(taskType);
        task.setBusinessKey(businessKey);
        task.setPayloadJson(writeJson(payload));
        task.setMaxAttempts(maxAttempts);
        mapper.insertIdempotent(task);
        return task.getId();
    }

    @Scheduled(fixedDelayString = "${orange.tasks.dispatch-delay-ms:2000}")
    public void dispatch() {
        for (SystemTask candidate : mapper.selectReady(DISPATCH_BATCH)) {
            if (mapper.claim(candidate.getId(), workerId) == 1) {
                try {
                    executor.execute(() -> executeClaimed(candidate.getId()));
                } catch (RuntimeException ex) {
                    mapper.fail(candidate.getId(), "RETRY_WAIT",
                            "Task executor rejected the job", LocalDateTime.now().plusSeconds(10));
                    log.warn("Task executor rejected id={} type={}", candidate.getId(), candidate.getTaskType());
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${orange.tasks.stale-recovery-delay-ms:60000}")
    public void recoverStaleTasks() {
        int recovered = mapper.recoverStale(15);
        if (recovered > 0) {
            log.warn("Recovered {} stale background tasks", recovered);
        }
    }

    private void executeClaimed(Long taskId) {
        SystemTask task = mapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) return;

        SystemTaskHandler handler = handlers.orderedStream()
                .filter(candidate -> candidate.taskType().equals(task.getTaskType()))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            mapper.fail(taskId, "FAILED", "No handler registered for " + task.getTaskType(), LocalDateTime.now());
            return;
        }

        try {
            Object result = handler.execute(task);
            mapper.complete(taskId, writeJson(result));
        } catch (Exception ex) {
            int attempts = task.getAttempts();
            boolean exhausted = task.getMaxAttempts() > 0 && attempts >= task.getMaxAttempts();
            String status = exhausted ? "FAILED" : "RETRY_WAIT";
            long delaySeconds = Math.min(1800, 15L << Math.min(attempts, 7));
            mapper.fail(taskId, status, abbreviate(ex.getMessage()), LocalDateTime.now().plusSeconds(delaySeconds));
            log.error("Task failed type={} id={} attempt={} status={}",
                    task.getTaskType(), taskId, attempts, status, ex);
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Task payload is not serializable", ex);
        }
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) return "Unknown task failure";
        return message.length() <= 1900 ? message : message.substring(0, 1900);
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception ignored) {
            return "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
