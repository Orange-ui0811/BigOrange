package com.orangejd.server.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AiRecordMetrics {
    private final MeterRegistry registry;

    public AiRecordMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void success(String recordType, String operation) {
        increment(recordType, operation, "success");
    }

    public void failure(String recordType, String operation) {
        increment(recordType, operation, "failure");
    }

    public void corrupted(String recordType) {
        increment(recordType, "read", "corrupted");
    }

    private void increment(String recordType, String operation, String outcome) {
        registry.counter("orange.ai.record.operations",
                "record_type", recordType,
                "operation", operation,
                "outcome", outcome).increment();
    }
}
