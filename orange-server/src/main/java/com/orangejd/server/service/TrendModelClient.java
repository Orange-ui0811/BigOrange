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

    @SuppressWarnings("unchecked")
    public String complete(AiRuntimeConfig runtime, Map<String, Object> body) {
        String url = runtime.endpoint().trim();
        if (!url.startsWith("http")) url = "https://" + url;
        if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + runtime.apiKey());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        if (response.getBody() == null) return "";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        return String.valueOf(message.getOrDefault("content", ""));
    }
}
