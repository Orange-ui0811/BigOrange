package com.orangejd.server.foundation.aitool;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class AiToolPoolProperties {
    private final boolean enabled;
    private final int globalMaxConcurrency;
    private final long acquireWaitMillis;
    private final long leaseMillis;
    private final String endpoint;
    private final String model;
    private final List<Slot> slots;

    public AiToolPoolProperties(
            @Value("${orange.ai-tool-pool.enabled:false}") boolean enabled,
            @Value("${orange.ai-tool-pool.global-max-concurrency:50}") int globalMaxConcurrency,
            @Value("${orange.ai-tool-pool.acquire-wait-millis:2000}") long acquireWaitMillis,
            @Value("${orange.ai-tool-pool.lease-millis:330000}") long leaseMillis,
            @Value("${orange.ai-tool-pool.endpoint:}") String endpoint,
            @Value("${orange.ai-tool-pool.model:}") String model,
            @Value("${orange.ai-tool-pool.keys[0]:}") String key1,
            @Value("${orange.ai-tool-pool.keys[1]:}") String key2,
            @Value("${orange.ai-tool-pool.keys[2]:}") String key3,
            @Value("${orange.ai-tool-pool.keys[3]:}") String key4,
            @Value("${orange.ai-tool-pool.keys[4]:}") String key5,
            @Value("${orange.ai-tool-pool.keys[5]:}") String key6,
            @Value("${orange.ai-tool-pool.keys[6]:}") String key7,
            @Value("${orange.ai-tool-pool.keys[7]:}") String key8,
            @Value("${orange.ai-tool-pool.keys[8]:}") String key9,
            @Value("${orange.ai-tool-pool.keys[9]:}") String key10) {
        this.enabled = enabled;
        this.globalMaxConcurrency = Math.max(1, Math.min(globalMaxConcurrency, 80));
        this.acquireWaitMillis = Math.max(0, Math.min(acquireWaitMillis, 10_000));
        this.leaseMillis = Math.max(60_000, Math.min(leaseMillis, 600_000));
        this.endpoint = endpoint == null ? "" : endpoint.trim().replaceAll("/+$", "");
        this.model = model == null ? "" : model.trim();
        List<String> values = List.of(key1, key2, key3, key4, key5, key6, key7, key8, key9, key10);
        List<Slot> configured = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String key = values.get(index) == null ? "" : values.get(index).trim();
            if (StringUtils.hasText(key)) {
                configured.add(new Slot(index + 1, key, fingerprint(key)));
            }
        }
        this.slots = List.copyOf(configured);
    }

    public boolean enabled() { return enabled; }
    public int globalMaxConcurrency() { return globalMaxConcurrency; }
    public long acquireWaitMillis() { return acquireWaitMillis; }
    public long leaseMillis() { return leaseMillis; }
    public List<Slot> slots() { return slots; }
    public boolean poolReady() {
        return StringUtils.hasText(endpoint) && StringUtils.hasText(model) && !slots.isEmpty();
    }
    public AiRuntimeConfig config(Slot slot) {
        return new AiRuntimeConfig("tool_pool", "env-slot-" + slot.number(),
                endpoint, slot.apiKey(), model, "TOOL_POOL");
    }

    private String fingerprint(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Key 指纹", e);
        }
    }

    public record Slot(int number, String apiKey, String fingerprint) {}
}
