package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;

import java.util.*;

@Service
public class JobMatchService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchService.class);

    @Autowired(required = false) private VectorStore vectorStore;
    @Autowired private QuotaServiceImpl quotaService;
    @Autowired private PricingService pricingService;

    @Value("${orange.ai-trend.endpoint:}")
    private String endpoint;
    @Value("${orange.ai-trend.api-key:}")
    private String apiKey;
    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    @Autowired(required = false) private AiRuntimeConfigService aiConfigService;

    @Autowired
    public JobMatchService(ObjectMapper objectMapper) {
        this(objectMapper, createRestTemplate());
    }

    JobMatchService(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> match(String planContent) {
        return match(planContent, "ai:match", "岗位匹配");
    }

    public Map<String, Object> match(String planContent, String pricingKey, String quotaDescription) {
        Long accountId = BaseContext.getCurrentId();
        String requestId = "match_" + accountId + "_" + System.currentTimeMillis();
        int chargedCost = 0;

        // 预扣
        if (accountId != null && accountId > 0) {
            try { chargedCost = pricingService.getCost(pricingKey); quotaService.consume(accountId, chargedCost, quotaDescription, requestId); }
            catch (Exception e) { throw new RuntimeException(e.getMessage()); }
        }

        try {
            // 检索相关 JD
            List<Document> jobDocs = Collections.emptyList();
            if (vectorStore != null) {
                try {
                    jobDocs = vectorStore.similaritySearch(
                        SearchRequest.builder().query(planContent).topK(5).similarityThreshold(0.5).build()
                    );
                } catch (Exception e) { log.warn("检索失败: {}", e.getMessage()); }
            }

            // 拼 prompt
            StringBuilder jdContext = new StringBuilder();
            for (Document d : jobDocs) {
                jdContext.append("- ").append(d.getText().substring(0, Math.min(300, d.getText().length()))).append("\n");
            }

            String prompt = "你是岗位匹配专家。用户已完成技术方案设计，需要根据方案内容匹配最合适的AI实习岗位。" +
                "请仔细分析方案中的技术栈、项目模块、架构设计等具体内容，与知识库中的岗位JD进行精准匹配。\n\n" +
                "## 用户方案\n" + planContent + "\n\n" +
                "## 可匹配的岗位\n" + jdContext + "\n\n" +
                "要求：\n" +
                "1. 推荐3个最匹配的岗位，不编造岗位\n" +
                "2. reason（100字以上）：详细说明方案中的哪些技术点、模块、设计思路与该岗位要求匹配\n" +
                "3. focus（100字以上）：明确指出方案中哪些具体部分（如模块名、技术栈、架构决策）需要重点深入学习才能胜任该岗位，并解释为什么这些部分是面试官关注的重点\n" +
                "4. 不返回公司名\n\n" +
                "输出严格JSON（不要markdown代码块）：\n" +
                "{\"matches\":[{\"position\":\"岗位名\",\"reason\":\"详细匹配原因\",\"focus\":\"详细学习重点\"}]}";

            // 调 AI
            AiRuntimeConfig runtime = runtimeConfig();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", runtime.model());
            List<Map<String, String>> msgs = new ArrayList<>();
            msgs.add(Map.of("role", "user", "content", prompt));
            body.put("messages", msgs);
            body.put("temperature", 0.3);

            String url = runtime.endpoint().trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(runtime.apiKey());
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            @SuppressWarnings("unchecked")
            String content = (String) ((Map<String, Object>) ((List<Map<String, Object>>)
                    resp.getBody().get("choices")).get(0).get("message")).get("content");
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            // Only confirm success after the AI response is valid and renderable by the client.
            List<Map<String, String>> matches = parseMatches(content);

            log.info("[JOB_MATCH] 匹配完成 accountId={}", accountId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("result", content);
            result.put("matches", matches);
            return result;
        } catch (Exception e) {
            if (accountId != null && accountId > 0) {
                try { quotaService.refund(accountId, chargedCost, requestId, quotaDescription + "失败退款"); } catch (Exception ignored) {}
            }
            log.warn("[JOB_MATCH] 匹配失败并已尝试退款 accountId={}, reason={}", accountId, e.getMessage());
            throw new RuntimeException("AI匹配失败，请稍后重试");
        }
    }

    private AiRuntimeConfig runtimeConfig() {
        return aiConfigService == null
                ? new AiRuntimeConfig(AiRuntimeConfigService.CONTRIBUTION, "env",
                        endpoint, apiKey, model, "ENV")
                : aiConfigService.current(AiRuntimeConfigService.CONTRIBUTION);
    }

    List<Map<String, String>> parseMatches(String content) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        JsonNode items = root.path("matches");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalArgumentException("AI返回缺少岗位匹配结果");
        }

        List<Map<String, String>> matches = new ArrayList<>();
        for (JsonNode item : items) {
            String position = requiredText(item, "position");
            String reason = requiredText(item, "reason");
            String focus = requiredText(item, "focus");
            matches.add(Map.of("position", position, "reason", reason, "focus", focus));
        }
        return matches;
    }

    private String requiredText(JsonNode item, String field) {
        String value = item.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("AI返回字段不完整: " + field);
        return value;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(110_000);
        return new RestTemplate(factory);
    }
}
