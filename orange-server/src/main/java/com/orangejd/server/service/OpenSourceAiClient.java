package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenSourceAiClient {

    private final RestTemplate restTemplate;

    @Value("${orange.ai-trend.endpoint:}") private String endpoint;
    @Value("${orange.ai-trend.api-key:}") private String apiKey;
    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}") private String model;

    public OpenSourceAiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public String call(String systemPrompt, String userPrompt, double temperature) {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(apiKey)) {
            throw new BaseException("AI 服务尚未配置");
        }
        String url = endpoint.trim();
        if (!url.startsWith("http")) url = "https://" + url;
        if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", temperature);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) throw new BaseException("AI 返回为空");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) throw new BaseException("AI 返回缺少 choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = message == null ? null : String.valueOf(message.get("content"));
        if (!StringUtils.hasText(content)) throw new BaseException("AI 返回内容为空");
        return content.replaceAll("```json\\s*", "").replaceAll("```(?:markdown)?\\s*", "").trim();
    }
}
