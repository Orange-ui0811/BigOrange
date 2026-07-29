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
    private final MeterRegistry metrics;
    private final ScheduledExecutorService renewer =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ai-tool-lease-renewer");
                thread.setDaemon(true);
                return thread;
            });

    public AiToolRouter(AiToolPoolProperties properties, AiToolSlotLeaseService leases,
                        AiRuntimeConfigService configService, AiToolCallLogMapper callLogMapper,
                        MeterRegistry metrics) {
        this.properties = properties;
        this.leases = leases;
        this.configService = configService;
        this.callLogMapper = callLogMapper;
        this.metrics = metrics;
    }

    public <T> T execute(AiToolContext context, Operation<T> operation) {
        if (!properties.enabled()) {
            return operation.run(configService.current(context.legacyConfigType()));
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
            insertLog(context, provider, slot, config);
            AiToolSlotLeaseService.SlotLease leasedSlot = slot;
            renewal = renewer.scheduleAtFixedRate(
                    () -> renewSafely(global, leasedSlot), 30, 30, TimeUnit.SECONDS);
            T result = operation.run(config);
            finish(context.requestId(), "SUCCEEDED", null, null, null);
            metrics.counter("orange.ai.tool.calls", "provider", provider,
                    "outcome", "success").increment();
            return result;
        } catch (RuntimeException exception) {
            boolean timeout = causedByTimeout(exception);
            AiToolProviderException providerFailure = AiToolProviderException.from(exception);
            if (slot != null && providerFailure != null) {
                if (providerFailure.httpStatus() == 401 || providerFailure.httpStatus() == 403) {
                    leases.setInvalid(slot.slot().number());
                } else if (providerFailure.httpStatus() == 429) {
                    leases.setThrottled(slot.slot().number(), providerFailure.retryAfterMillis());
                }
            }
            finish(context.requestId(), timeout ? "TIMEOUT" : "FAILED",
                    providerFailure == null ? null : providerFailure.httpStatus(),
                    timeout ? "TIMEOUT" : providerFailure == null
                            ? exception.getClass().getSimpleName() : providerFailure.errorCode(),
                    safeMessage(exception));
            metrics.counter("orange.ai.tool.calls", "provider", provider,
                    "outcome", timeout ? "timeout" : "failure").increment();
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
        return Map.of(
                "enabled", properties.enabled(),
                "configuredSlots", properties.slots().size(),
                "globalMax", properties.globalMaxConcurrency(),
                "globalRunning", global,
                "giteeRunning", gitee,
                "fallbackRunning", Math.max(0, global - gitee),
                "slots", slots);
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

    private void insertLog(AiToolContext context, String provider,
                           AiToolSlotLeaseService.SlotLease slot, AiRuntimeConfig config) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("requestId", context.requestId());
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

    private void finish(String requestId, String status, Integer httpStatus,
                        String errorCode, String errorSummary) {
        try {
            callLogMapper.finish(requestId, status, httpStatus, errorCode, errorSummary);
        } catch (RuntimeException exception) {
            log.error("AI_TOOL_LOG_FINISH_FAILED requestId={} status={}", requestId, status, exception);
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
