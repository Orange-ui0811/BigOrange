package com.orangejd.server.service;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class TrendModelClient {
    private final RestTemplate restTemplate;

    public TrendModelClient() {
        restTemplate = new RestTemplate();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        restTemplate.setRequestFactory(factory);
    }

    TrendModelClient(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public record Completion(String content, Map<String, Object> metrics) {}

    @SuppressWarnings("unchecked")
    public String complete(AiRuntimeConfig runtime, Map<String, Object> body) {
        return completeMeasured(runtime, body).content();
    }

    @SuppressWarnings("unchecked")
    public Completion completeMeasured(AiRuntimeConfig runtime, Map<String, Object> body) {
        long started = System.nanoTime();
        String url = runtime.endpoint().trim();
        if (!url.startsWith("http")) url = "https://" + url;
        if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + runtime.apiKey());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        if (response.getBody() == null) throw new IllegalStateException("AI返回为空");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) throw new IllegalStateException("AI返回缺少choices");
        Object finishReason = choices.getFirst().get("finish_reason");
        if ("length".equals(finishReason) || "content_filter".equals(finishReason))
            throw new IllegalStateException("AI输出未完成: " + finishReason);
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        if (message == null || !(message.get("content") instanceof String content) || content.isBlank())
            throw new IllegalStateException("AI返回缺少文本内容");
        Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("model", response.getBody().getOrDefault("model", runtime.model()));
        metrics.put("runtimeSource", runtime.source());
        metrics.put("providerHost", java.net.URI.create(url).getHost());
        metrics.put("requestedModel", runtime.model());
        metrics.put("durationMs", (System.nanoTime() - started) / 1_000_000.0);
        metrics.put("finishReason", finishReason);
        Map<String, Object> usage = new java.util.LinkedHashMap<>();
        if (response.getBody().get("usage") instanceof Map<?, ?> reported) {
            for (String key : List.of("prompt_tokens", "completion_tokens", "total_tokens",
                    "prompt_cache_hit_tokens", "prompt_cache_miss_tokens")) {
                Object value = reported.get(key);
                if (value instanceof Number n && n.longValue() >= 0) usage.put(key, n.longValue());
            }
            for (String key : List.of("prompt_tokens_details", "completion_tokens_details")) {
                if (reported.get(key) instanceof Map<?, ?> details) {
                    Map<String, Long> counts = new java.util.LinkedHashMap<>();
                    details.forEach((name, value) -> {
                        if (value instanceof Number n && n.longValue() >= 0) counts.put(String.valueOf(name), n.longValue());
                    });
                    usage.put(key, counts);
                }
            }
        }
        metrics.put("usageSource", usage.isEmpty() ? "UNAVAILABLE" : "PROVIDER");
        metrics.put("usage", usage);
        return new Completion(content, metrics);
    }
}
