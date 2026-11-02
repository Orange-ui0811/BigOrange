package com.orangejd.server.contribution.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int JOB_AUDIT_MAX_TOKENS = 800;
    private static final Set<String> ACTIONS = Set.of("APPROVE", "REJECT", "MANUAL_REVIEW");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AiRuntimeConfigService configService;
    private final AiRuntimeConfig staticConfig;

    @Autowired
    public JobAiAuditClient(ObjectMapper objectMapper,
                            RestTemplateBuilder builder,
                            AiRuntimeConfigService configService) {
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
        this.configService = configService;
        this.staticConfig = null;
    }

    JobAiAuditClient(ObjectMapper objectMapper, RestTemplateBuilder builder,
                     String endpoint, String apiKey, String model) {
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
        this.configService = null;
        this.staticConfig = new AiRuntimeConfig("audit", "env",
                endpoint == null ? "" : endpoint.trim(),
                apiKey == null ? "" : apiKey.trim(), model, "ENV");
    }

    public boolean isConfigured() {
        AiRuntimeConfig config = config();
        return config.endpoint().startsWith("http") && !config.apiKey().isBlank();
    }

    public Decision review(JobPosting job) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI audit endpoint or API key is not configured");
        }
        AiRuntimeConfig config = config();

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
                "model", config.model(),
                "temperature", 0.0,
                "max_tokens", JOB_AUDIT_MAX_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content", "严格审核，无法确认时必须转人工。"),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        JsonNode response = restTemplate.postForObject(resolveUrl(config.endpoint()),
                new HttpEntity<>(body, headers), JsonNode.class);
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

    private String resolveUrl(String endpoint) {
        return endpoint.endsWith("/chat/completions")
                ? endpoint
                : endpoint.replaceAll("/+$", "") + "/chat/completions";
    }

    private AiRuntimeConfig config() {
        return configService == null ? staticConfig : configService.current(AiRuntimeConfigService.AUDIT);
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
