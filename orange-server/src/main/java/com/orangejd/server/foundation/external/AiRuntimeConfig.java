package com.orangejd.server.foundation.external;

public record AiRuntimeConfig(
        String type,
        String version,
        String endpoint,
        String apiKey,
        String model,
        String source) {
}
