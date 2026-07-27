package com.orangejd.server.service;

import com.orangejd.server.foundation.external.ExternalAiClient;
import org.springframework.stereotype.Component;

@Component
public class OpenSourceAiClient {
    private static final ThreadLocal<String> PINNED_VERSION = new ThreadLocal<>();
    private final ExternalAiClient aiClient;

    public OpenSourceAiClient(ExternalAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public String call(String systemPrompt, String userPrompt, double temperature) {
        return aiClient.callContribution(null, systemPrompt, userPrompt,
                temperature, "open-source");
    }

    public String callText(String systemPrompt, String userPrompt, double temperature) {
        return aiClient.callTrend(PINNED_VERSION.get(), systemPrompt, userPrompt,
                temperature, "open-source-translation");
    }

    public <T> T withVersion(String version, java.util.function.Supplier<T> action) {
        String previous = PINNED_VERSION.get();
        PINNED_VERSION.set(version);
        try {
            return action.get();
        } finally {
            if (previous == null) PINNED_VERSION.remove();
            else PINNED_VERSION.set(previous);
        }
    }
}
