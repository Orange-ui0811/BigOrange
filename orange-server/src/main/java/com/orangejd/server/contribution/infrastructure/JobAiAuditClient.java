package com.orangejd.server.contribution.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.JobPosting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JobAiAuditClient {
    private static final Set<String> ACTIONS = Set.of("APPROVE", "REJECT", "MANUAL_REVIEW");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public JobAiAuditClient(ObjectMapper objectMapper,
                            RestTemplateBuilder builder,
                            @Value("${orange.ai-audit.endpoint:}") String endpoint,
                            @Value("${orange.ai-audit.api-key:}") String apiKey,
                            @Value("${orange.ai-audit.model:deepseek-v4-pro}") String model) {
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public boolean isConfigured() {
        return endpoint.startsWith("http") && !apiKey.isBlank();
    }

    public Decision review(JobPosting job) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI audit endpoint or API key is not configured");
        }

        String prompt = """
                你是 OrangeVC 的岗位投稿审核员。判断内容是否是有学习参考价值、信息完整且不包含违法、
                仇恨、色情、诈骗、明显广告或敏感个人信息的真实岗位说明。
                仅在内容明确安全、岗位名称与正文一致、信息足够完整时选择 APPROVE。
                明确违规或完全无效时选择 REJECT；任何不确定、信息不足或需要人工判断的情况选择 MANUAL_REVIEW。
                只输出 JSON：
                {"action":"APPROVE|REJECT|MANUAL_REVIEW","confidence":0.0,"reason":"简短依据"}

                岗位：%s
                方向：%s
                内容：
                %s
                """.formatted(safe(job.getPosition()), safe(job.getDirection()), truncate(job.getDisplayContent(), 6000));

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.0,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", "严格审核，无法确认时必须转人工。"),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        JsonNode response = restTemplate.postForObject(resolveUrl(), new HttpEntity<>(body, headers), JsonNode.class);
        String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText();
        return parseDecision(content);
    }

    Decision parseDecision(String content) {
        try {
            String normalized = content == null ? "" :
                    content.replace("```json", "").replace("```", "").trim();
            JsonNode json = objectMapper.readTree(normalized);
            String action = json.path("action").asText("").toUpperCase();
            double confidence = json.path("confidence").asDouble(-1);
            String reason = json.path("reason").asText("").trim();
            if (!ACTIONS.contains(action) || confidence < 0 || confidence > 1 || reason.isBlank()) {
                return new Decision("MANUAL_REVIEW", 0, "AI 返回格式不完整，需要人工审核");
            }
            return new Decision(action, confidence, truncate(reason, 900));
        } catch (Exception ex) {
            return new Decision("MANUAL_REVIEW", 0, "无法可靠解析 AI 审核结果，需要人工审核");
        }
    }

    private String resolveUrl() {
        return endpoint.endsWith("/chat/completions")
                ? endpoint
                : endpoint.replaceAll("/+$", "") + "/chat/completions";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        String safe = safe(value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public record Decision(String action, double confidence, String reason) {}
}
