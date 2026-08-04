package com.orangejd.server.foundation.aitool;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AiToolRouter {
    private static final Logger log = LoggerFactory.getLogger(AiToolRouter.class);
    private final AiToolPoolProperties properties;
    private final AiToolSlotLeaseService leases;
    private final AiRuntimeConfigService configService;
    private final AiToolCallLogMapper callLogMapper;
    private final AiResultRecorder resultRecorder;
    private final MeterRegistry metrics;
    private final ScheduledExecutorService renewer =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ai-tool-lease-renewer");
                thread.setDaemon(true);
                return thread;
            });

    public AiToolRouter(AiToolPoolProperties properties, AiToolSlotLeaseService leases,
                        AiRuntimeConfigService configService, AiToolCallLogMapper callLogMapper,
                        AiResultRecorder resultRecorder, MeterRegistry metrics) {
        this.properties = properties;
        this.leases = leases;
        this.configService = configService;
        this.callLogMapper = callLogMapper;
        this.resultRecorder = resultRecorder;
        this.metrics = metrics;
    }

    public <T> T execute(AiToolContext context, Operation<T> operation) {
        int attemptNo = 1;
        while (true) {
            try {
                return executeAttempt(context, operation, attemptNo);
            } catch (RuntimeException exception) {
                if (!canRetry(attemptNo, exception)) throw exception;
                long delayMillis = retryDelayMillis(attemptNo, exception);
                log.warn("AI_TOOL_AUTO_RETRY requestId={} attempt={} delayMs={} reason={}",
                        context.requestId(), attemptNo, delayMillis, safeMessage(exception));
                sleepBeforeRetry(delayMillis);
                if (attemptNo < Integer.MAX_VALUE) attemptNo++;
            }
        }
    }

    private <T> T executeAttempt(AiToolContext context, Operation<T> operation, int attemptNo) {
        if (!properties.enabled()) {
            T result = operation.run(configService.current(context.legacyConfigType()));
            resultRecorder.saveRequired(context, result);
            return result;
        }
        if (!properties.poolReady()) {
            throw new BaseException("AI 工具调用池尚未完整配置");
        }
        AiToolSlotLeaseService.GlobalLease global = acquireGlobal(context);
        AiToolSlotLeaseService.SlotLease slot = null;
        ScheduledFuture<?> renewal = null;
        String provider = "UNKNOWN";
        try {
            slot = leases.acquireSlot(properties.slots(), context, properties.leaseMillis()).orElse(null);
            AiRuntimeConfig config = slot == null
                    ? configService.current(AiRuntimeConfigService.TOOL_FALLBACK)
                    : properties.config(slot.slot());
            provider = slot == null ? "DEEPSEEK" : "GITEE";
            insertLog(context, attemptNo, provider, slot, config);
            AiToolSlotLeaseService.SlotLease leasedSlot = slot;
            renewal = renewer.scheduleAtFixedRate(
                    () -> renewSafely(global, leasedSlot), 30, 30, TimeUnit.SECONDS);
            T result = operation.run(config);
            resultRecorder.saveRequired(context, result);
            finish(context.requestId(), attemptNo, "SUCCEEDED", null, null, null);
            metrics.counter("orange.ai.tool.calls", "provider", provider,
                    "outcome", "success").increment();
            return result;
        } catch (RuntimeException exception) {
            boolean timeout = causedByTimeout(exception);
            AiToolProviderException providerFailure = AiToolProviderException.from(exception);
            boolean retrying = canRetry(attemptNo, exception);
            if (slot != null) {
                if (providerFailure != null
                        && (providerFailure.httpStatus() == 401 || providerFailure.httpStatus() == 403)) {
                    leases.setInvalid(slot.slot().number());
                } else if (providerFailure != null && providerFailure.httpStatus() == 429) {
                    leases.setThrottled(slot.slot().number(), providerFailure.retryAfterMillis());
                } else if (retrying) {
                    long retryDelay = retryDelayMillis(attemptNo, exception);
                    leases.setThrottled(slot.slot().number(), Math.max(5000,
                            Math.min(30_000, retryDelay + 5000)));
                }
            }
            finish(context.requestId(), attemptNo, retrying ? "RETRY_WAIT" : timeout ? "TIMEOUT" : "FAILED",
                    providerFailure == null ? null : providerFailure.httpStatus(),
                    timeout ? "TIMEOUT" : providerFailure == null
                            ? exception.getClass().getSimpleName() : providerFailure.errorCode(),
                    safeMessage(exception));
            metrics.counter("orange.ai.tool.calls", "provider", provider,
                    "outcome", retrying ? "retry" : timeout ? "timeout" : "failure").increment();
            throw exception;
        } finally {
            if (renewal != null) renewal.cancel(false);
            releaseSafely(global, slot);
        }
    }

    public Map<String, Object> runtime() {
        long global = leases.activeGlobal();
        long gitee = properties.slots().stream()
                .filter(slot -> leases.slotBusy(slot.number())).count();
        List<Map<String, Object>> slots = properties.slots().stream().map(slot -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("slotNo", slot.number());
            value.put("fingerprint", slot.fingerprint());
            value.put("status", leases.status(slot.number()));
            return value;
        }).toList();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("enabled", properties.enabled());
        runtime.put("configuredSlots", properties.slots().size());
        runtime.put("globalMax", properties.globalMaxConcurrency());
        runtime.put("globalRunning", global);
        runtime.put("giteeRunning", gitee);
        runtime.put("fallbackRunning", Math.max(0, global - gitee));
        runtime.put("autoRetryEnabled", properties.autoRetryEnabled());
        runtime.put("autoRetryMaxAttempts", properties.autoRetryMaxAttempts());
        runtime.put("slots", slots);
        return runtime;
    }

    public List<Map<String, Object>> recentCalls(int limit) {
        return callLogMapper.recent(Math.max(1, Math.min(limit, 200)));
    }

    public void setSlotDisabled(int slotNo, boolean disabled) {
        if (slotNo < 1 || slotNo > 10) throw new BaseException("槽位编号无效");
        leases.setDisabled(slotNo, disabled);
    }

    public void resetSlotStatus(int slotNo) {
        if (slotNo < 1 || slotNo > 10) throw new BaseException("槽位编号无效");
        leases.resetStatus(slotNo);
    }

    private AiToolSlotLeaseService.GlobalLease acquireGlobal(AiToolContext context) {
        long deadline = System.currentTimeMillis() + properties.acquireWaitMillis();
        do {
            Optional<AiToolSlotLeaseService.GlobalLease> lease = leases.acquireGlobal(
                    context, properties.globalMaxConcurrency(), properties.leaseMillis());
            if (lease.isPresent()) return lease.get();
            if (System.currentTimeMillis() >= deadline) break;
            try {
                Thread.sleep(Math.min(50, Math.max(1, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (true);
        metrics.counter("orange.ai.tool.capacity.rejected").increment();
        throw new BaseException("AI 服务繁忙，请稍后重试");
    }

    private void insertLog(AiToolContext context, int attemptNo, String provider,
                           AiToolSlotLeaseService.SlotLease slot, AiRuntimeConfig config) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("requestId", context.requestId());
        record.put("attemptNo", attemptNo);
        record.put("accountId", context.accountId());
        record.put("businessType", context.businessType());
        record.put("businessId", context.businessId());
        record.put("purpose", context.purpose());
        record.put("provider", provider);
        record.put("slotNo", slot == null ? null : slot.slot().number());
        record.put("keyFingerprint", slot == null ? null : slot.slot().fingerprint());
        record.put("model", config.model());
        callLogMapper.insert(record);
    }

    private void finish(String requestId, int attemptNo, String status, Integer httpStatus,
                        String errorCode, String errorSummary) {
        try {
            callLogMapper.finish(requestId, attemptNo, status, httpStatus, errorCode, errorSummary);
        } catch (RuntimeException exception) {
            log.error("AI_TOOL_LOG_FINISH_FAILED requestId={} status={}", requestId, status, exception);
        }
    }

    private boolean canRetry(int attemptNo, RuntimeException exception) {
        if (!properties.autoRetryEnabled() || !AiToolRetryPolicy.isRetryable(exception)) return false;
        int maxAttempts = properties.autoRetryMaxAttempts();
        return maxAttempts == 0 || attemptNo < maxAttempts;
    }

    private long retryDelayMillis(int attemptNo, RuntimeException exception) {
        AiToolProviderException provider = AiToolProviderException.from(exception);
        if (provider != null && provider.retryAfterMillis() > 0) {
            return Math.min(properties.autoRetryMaxDelayMillis(), provider.retryAfterMillis());
        }
        long initial = properties.autoRetryInitialDelayMillis();
        if (initial == 0) return 0;
        int shifts = Math.min(Math.max(0, attemptNo - 1), 10);
        long calculated = initial > (Long.MAX_VALUE >> shifts) ? Long.MAX_VALUE : initial << shifts;
        return Math.min(properties.autoRetryMaxDelayMillis(), calculated);
    }

    private void sleepBeforeRetry(long delayMillis) {
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BaseException("AI 分析已中断，请在 AI 记录中确认结果");
        }
    }

    private void renewSafely(AiToolSlotLeaseService.GlobalLease global,
                             AiToolSlotLeaseService.SlotLease slot) {
        try { leases.renew(global, slot); }
        catch (RuntimeException exception) {
            log.error("AI_TOOL_LEASE_RENEW_FAILED leaseId={}", global.leaseId(), exception);
        }
    }

    private void releaseSafely(AiToolSlotLeaseService.GlobalLease global,
                               AiToolSlotLeaseService.SlotLease slot) {
        if (global == null) return;
        try { leases.release(global, slot); }
        catch (RuntimeException exception) {
            log.error("AI_TOOL_LEASE_RELEASE_FAILED leaseId={}", global.leaseId(), exception);
        }
    }

    private boolean causedByTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) return true;
        }
        return false;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) return throwable.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer <redacted>");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }

    @PreDestroy
    public void shutdown() {
        renewer.shutdownNow();
    }

    @FunctionalInterface
    public interface Operation<T> {
        T run(AiRuntimeConfig config);
    }
}
