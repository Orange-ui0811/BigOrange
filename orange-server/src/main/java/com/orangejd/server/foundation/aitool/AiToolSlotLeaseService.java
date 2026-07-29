package com.orangejd.server.foundation.aitool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiToolSlotLeaseService {
    private static final String GLOBAL_KEY = "orangevc:ai-tool:global:leases";
    private static final String SLOT_PREFIX = "orangevc:ai-tool:slot:";
    private static final String DISABLED_PREFIX = "orangevc:ai-tool:disabled:";
    private static final String INVALID_PREFIX = "orangevc:ai-tool:invalid:";
    private static final String THROTTLED_PREFIX = "orangevc:ai-tool:throttled:";
    private static final DefaultRedisScript<Long> ACQUIRE_GLOBAL = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then return 0 end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_IF_VALUE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> ACQUIRE_SLOT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2], KEYS[3], KEYS[4]) > 0 then return 0 end
            if redis.call('EXISTS', KEYS[1]) > 0 then return 0 end
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_IF_VALUE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_GLOBAL = new DefaultRedisScript<>("""
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
              redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AtomicInteger cursor = new AtomicInteger();

    public AiToolSlotLeaseService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<GlobalLease> acquireGlobal(AiToolContext context, int max, long leaseMillis) {
        String leaseId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Long acquired = redis.execute(ACQUIRE_GLOBAL, List.of(GLOBAL_KEY),
                String.valueOf(now), String.valueOf(now + leaseMillis),
                String.valueOf(max), leaseId);
        return Long.valueOf(1).equals(acquired)
                ? Optional.of(new GlobalLease(leaseId, leaseMillis)) : Optional.empty();
    }

    public Optional<SlotLease> acquireSlot(List<AiToolPoolProperties.Slot> slots,
                                           AiToolContext context, long leaseMillis) {
        if (slots.isEmpty()) return Optional.empty();
        int start = Math.floorMod(cursor.getAndIncrement(), slots.size());
        for (int offset = 0; offset < slots.size(); offset++) {
            AiToolPoolProperties.Slot slot = slots.get((start + offset) % slots.size());
            String leaseId = UUID.randomUUID().toString();
            String value = leaseValue(leaseId, context);
            Long acquired = redis.execute(ACQUIRE_SLOT, List.of(
                            SLOT_PREFIX + slot.number(),
                            DISABLED_PREFIX + slot.number(),
                            INVALID_PREFIX + slot.number(),
                            THROTTLED_PREFIX + slot.number()),
                    value, String.valueOf(leaseMillis));
            if (Long.valueOf(1).equals(acquired)) {
                return Optional.of(new SlotLease(leaseId, value, slot, leaseMillis));
            }
        }
        return Optional.empty();
    }

    public void renew(GlobalLease global, SlotLease slot) {
        long expiry = System.currentTimeMillis() + global.leaseMillis();
        redis.execute(RENEW_GLOBAL, List.of(GLOBAL_KEY),
                global.leaseId(), String.valueOf(expiry));
        if (slot != null) {
            redis.execute(RENEW_IF_VALUE, List.of(SLOT_PREFIX + slot.slot().number()),
                    slot.value(), String.valueOf(slot.leaseMillis()));
        }
    }

    public void release(GlobalLease global, SlotLease slot) {
        if (slot != null) {
            redis.execute(RELEASE_IF_VALUE, List.of(SLOT_PREFIX + slot.slot().number()), slot.value());
        }
        redis.opsForZSet().remove(GLOBAL_KEY, global.leaseId());
    }

    public void setDisabled(int slotNo, boolean disabled) {
        String key = DISABLED_PREFIX + slotNo;
        if (disabled) redis.opsForValue().set(key, "1");
        else redis.delete(key);
    }

    public boolean disabled(int slotNo) {
        return Boolean.TRUE.equals(redis.hasKey(DISABLED_PREFIX + slotNo));
    }

    public void setInvalid(int slotNo) {
        redis.opsForValue().set(INVALID_PREFIX + slotNo, "1");
    }

    public void setThrottled(int slotNo, long retryAfterMillis) {
        if (retryAfterMillis <= 0) return;
        redis.opsForValue().set(THROTTLED_PREFIX + slotNo, "1",
                java.time.Duration.ofMillis(retryAfterMillis));
    }

    public void resetStatus(int slotNo) {
        redis.delete(List.of(INVALID_PREFIX + slotNo, THROTTLED_PREFIX + slotNo));
    }

    public String status(int slotNo) {
        if (disabled(slotNo)) return "DISABLED";
        if (Boolean.TRUE.equals(redis.hasKey(INVALID_PREFIX + slotNo))) return "INVALID";
        if (Boolean.TRUE.equals(redis.hasKey(THROTTLED_PREFIX + slotNo))) return "THROTTLED";
        return slotBusy(slotNo) ? "BUSY" : "AVAILABLE";
    }

    public long activeGlobal() {
        redis.opsForZSet().removeRangeByScore(GLOBAL_KEY, Double.NEGATIVE_INFINITY,
                System.currentTimeMillis());
        Long size = redis.opsForZSet().zCard(GLOBAL_KEY);
        return size == null ? 0 : size;
    }

    public boolean slotBusy(int slotNo) {
        return Boolean.TRUE.equals(redis.hasKey(SLOT_PREFIX + slotNo));
    }

    private String leaseValue(String leaseId, AiToolContext context) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "leaseId", leaseId,
                    "requestId", context.requestId(),
                    "accountId", context.accountId() == null ? 0 : context.accountId(),
                    "purpose", context.purpose(),
                    "businessId", context.businessId(),
                    "startedAt", Instant.now().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 AI 槽位租约", e);
        }
    }

    public record GlobalLease(String leaseId, long leaseMillis) {}
    public record SlotLease(String leaseId, String value, AiToolPoolProperties.Slot slot,
                            long leaseMillis) {}
}
