package com.orangejd.server.foundation.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class SystemTaskService {
    private static final Logger log = LoggerFactory.getLogger(SystemTaskService.class);
    private static final int DISPATCH_BATCH = 20;
    private static final int LEASE_SECONDS = 120;

    private final SystemTaskMapper mapper;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final ObjectProvider<SystemTaskHandler> handlers;
    private final IngestionRunService ingestionRunService;
    private final String workerId;
    private final Set<Long> activeRuns = ConcurrentHashMap.newKeySet();

    @Autowired
    public SystemTaskService(SystemTaskMapper mapper,
                             ObjectMapper objectMapper,
                             @Qualifier("orangeTaskExecutor") Executor executor,
                             ObjectProvider<SystemTaskHandler> handlers,
                             IngestionRunService ingestionRunService) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.handlers = handlers;
        this.ingestionRunService = ingestionRunService;
        this.workerId = resolveWorkerId();
    }

    SystemTaskService(SystemTaskMapper mapper,
                      ObjectMapper objectMapper,
                      Executor executor,
                      ObjectProvider<SystemTaskHandler> handlers) {
        this(mapper, objectMapper, executor, handlers, null);
    }

    public Long enqueue(String taskType, String businessKey, Object payload, int maxAttempts) {
        return enqueue(null, taskType, businessKey, payload, maxAttempts, null);
    }

    public Long enqueue(Long runId, String taskType, String businessKey, Object payload,
                        int maxAttempts, String configVersion) {
        SystemTask task = new SystemTask();
        task.setRunId(runId);
        task.setTaskType(taskType);
        task.setBusinessKey(businessKey);
        task.setPayloadJson(writeJson(payload));
        task.setMaxAttempts(maxAttempts);
        task.setConfigVersion(configVersion);
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
        int recovered = mapper.recoverStale();
        if (recovered > 0) {
            log.warn("Recovered {} stale background tasks", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${orange.tasks.heartbeat-delay-ms:30000}")
    public void heartbeat() {
        mapper.heartbeatWorker(workerId, LEASE_SECONDS);
        if (ingestionRunService != null) activeRuns.forEach(ingestionRunService::heartbeat);
    }

    private void executeClaimed(Long taskId) {
        SystemTask task = mapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) return;
        if (task.isCancelRequested()) {
            mapper.markCancelled(taskId);
            if (ingestionRunService != null) ingestionRunService.complete(task.getRunId(), false, "任务已取消");
            return;
        }
        if (task.getRunId() != null) {
            activeRuns.add(task.getRunId());
            if (ingestionRunService != null) ingestionRunService.running(task.getRunId());
        }

        SystemTaskHandler handler = handlers.orderedStream()
                .filter(candidate -> candidate.supports(task.getTaskType()))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            mapper.fail(taskId, "FAILED", "No handler registered for " + task.getTaskType(), LocalDateTime.now());
            if (ingestionRunService != null) {
                ingestionRunService.complete(task.getRunId(), false,
                        "No handler registered for " + task.getTaskType());
            }
            if (task.getRunId() != null) {
                activeRuns.remove(task.getRunId());
            }
            return;
        }

        try {
            Object result = handler.execute(task);
            SystemTask latest = mapper.selectById(taskId);
            if (latest != null && latest.isCancelRequested()) {
                if (ingestionRunService != null) ingestionRunService.complete(task.getRunId(), false, "任务已取消");
            } else {
                mapper.complete(taskId, writeJson(result));
                if (ingestionRunService != null) ingestionRunService.complete(task.getRunId(), true, null);
            }
        } catch (Exception ex) {
            int attempts = task.getAttempts();
            boolean exhausted = task.getMaxAttempts() > 0 && attempts >= task.getMaxAttempts();
            String status = exhausted ? "FAILED" : "RETRY_WAIT";
            long delaySeconds = Math.min(1800, 15L << Math.min(attempts, 7));
            mapper.fail(taskId, status, abbreviate(ex.getMessage()), LocalDateTime.now().plusSeconds(delaySeconds));
            if ("FAILED".equals(status)) {
                if (ingestionRunService != null) {
                    ingestionRunService.complete(task.getRunId(), false, abbreviate(ex.getMessage()));
                }
            } else {
                if (ingestionRunService != null) ingestionRunService.deferLease(task.getRunId(), delaySeconds);
            }
            log.error("Task failed type={} id={} attempt={} status={}",
                    task.getTaskType(), taskId, attempts, status, ex);
        } finally {
            if (task.getRunId() != null) {
                activeRuns.remove(task.getRunId());
            }
        }
    }

    public int cancelRun(Long runId) {
        return mapper.requestCancelByRun(runId);
    }

    public boolean hasRunningTask(Long runId) {
        return mapper.countRunningByRun(runId) > 0;
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
