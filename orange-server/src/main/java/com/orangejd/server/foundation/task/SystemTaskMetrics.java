package com.orangejd.server.foundation.task;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SystemTaskMetrics {
    private final SystemTaskMapper mapper;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public SystemTaskMetrics(SystemTaskMapper mapper, MeterRegistry registry) {
        this.mapper = mapper;
        for (String status : new String[]{"PENDING", "RUNNING", "RETRY_WAIT", "FAILED", "SUCCEEDED"}) {
            AtomicLong value = new AtomicLong();
            gauges.put(status, value);
            Gauge.builder("orangevc.tasks", value, AtomicLong::get)
                    .tag("status", status)
                    .description("Persistent background tasks by status")
                    .register(registry);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void refresh() {
        gauges.values().forEach(value -> value.set(0));
        for (Map<String, Object> row : mapper.countByStatus()) {
            String status = String.valueOf(row.get("status"));
            AtomicLong gauge = gauges.get(status);
            if (gauge != null && row.get("count") instanceof Number count) {
                gauge.set(count.longValue());
            }
        }
    }
}
