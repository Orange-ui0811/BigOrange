package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.aitool.AiToolContext;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import com.orangejd.server.foundation.aitool.AiToolRetryPolicy;
import com.orangejd.server.foundation.aitool.AiToolRouter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PlanGenerationService {
    public static final String PRICING_KEY = "ai:plan";
    private static final Logger log = LoggerFactory.getLogger(PlanGenerationService.class);
    private static final String[] PHASE_TITLES = {
            "", "需求理解", "成功标准定义", "约束风险识别", "可执行任务计划"
    };

    private final QuotaService quotaService;
    private final PricingService pricingService;
    private final RestTemplate restTemplate;
    @Autowired(required = false)
    private AiRuntimeConfigService aiConfigService;
    @Autowired(required = false)
    private AiToolRouter aiToolRouter;

    @Value("${orange.ai-trend.endpoint:}")
    private String apiEndpoint;
    @Value("${orange.ai-trend.api-key:}")
    private String apiKey;
    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String model;

    @Autowired
    public PlanGenerationService(QuotaService quotaService, PricingService pricingService) {
        this(quotaService, pricingService, createRestTemplate());
    }

    PlanGenerationService(QuotaService quotaService, PricingService pricingService,
                          RestTemplate restTemplate) {
        this.quotaService = quotaService;
        this.pricingService = pricingService;
        this.restTemplate = restTemplate;
    }

    public int cost() {
        return pricingService.getCost(PRICING_KEY);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(Long accountId, Map<String, Object> body) {
        int phase = integer(body.getOrDefault("phase", 1), "无效的阶段编号");
        if (phase < 1 || phase > 4) throw new BaseException("无效的阶段编号");
        String mode = text(body.getOrDefault("mode", "full"), 16);
        String summary = text(body.get("summary"), 10_000);
        String extra = text(body.get("extra"), 4_000);
        String prevContents = text(body.get("prevContents"), 30_000);
        if (!StringUtils.hasText(summary)) throw new BaseException("请输入项目描述");
        String operationRequestId = "plan_p" + phase + "_"
                + (accountId == null ? "guest" : accountId) + "_" + UUID.randomUUID();
        if (aiToolRouter == null) {
            return generateWithRuntime(accountId, phase, mode, summary, extra, prevContents,
                    operationRequestId, runtimeConfig());
        }
        AiToolContext context = new AiToolContext("plan.phase.generate", operationRequestId,
                accountId, "PLAN", String.valueOf(phase), AiRuntimeConfigService.PROJECT);
        return aiToolRouter.execute(context, runtime -> generateWithRuntime(
                accountId, phase, mode, summary, extra, prevContents, operationRequestId, runtime));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateWithRuntime(Long accountId, int phase, String mode,
                                                     String summary, String extra,
                                                     String prevContents, String operationRequestId,
                                                     AiRuntimeConfig runtime) {
        if (!StringUtils.hasText(runtime.endpoint()) || !StringUtils.hasText(runtime.apiKey())) {
            throw new BaseException("AI 方案设计服务尚未配置");
        }

        String prompt = "lite".equals(mode) && phase == 4
                ? buildPromptLite(summary, prevContents)
                : buildPrompt(phase, summary, extra, prevContents);
        String requestId = null;
        int chargedCost = 0;
        long balance = accountId == null ? 0 : quotaService.getBalance(accountId);
        if (accountId != null && accountId > 0) {
            chargedCost = cost();
            requestId = operationRequestId;
            balance = quotaService.consume(accountId, chargedCost,
                    "方案设计 Phase " + phase, requestId);
        }

        try {
            String url = runtime.endpoint().trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) {
                url = url.replaceAll("/$", "") + "/chat/completions";
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", runtime.model());
            request.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "你是专业的技术架构师和项目规划专家。请用 Markdown 输出结构清晰、可验证、可执行的方案。"),
                    Map.of("role", "user", "content", prompt)));
            request.put("temperature", 0.5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(runtime.apiKey());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(request, headers), Map.class);
            List<Map<String, Object>> choices = response.getBody() == null ? null
                    : (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) throw new IllegalStateException("AI returned no choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = Objects.toString(message == null ? null : message.get("content"), "").trim();
            if (!StringUtils.hasText(content)) throw new IllegalStateException("AI returned empty content");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("phase", phase);
            result.put("phaseTitle", PHASE_TITLES[phase]);
            result.put("segmentCost", chargedCost);
            result.put("balance", balance);
            return result;
        } catch (Exception e) {
            boolean retryManaged = aiToolRouter != null && AiToolRetryPolicy.isRetryable(e);
            if (accountId != null && chargedCost > 0 && !retryManaged) {
                quotaService.refund(accountId, chargedCost, requestId, "方案设计失败退款");
            }
            log.error("Plan generation failed: accountId={}, phase={}, mode={}",
                    accountId, phase, mode, e);
            AiToolProviderException providerFailure = AiToolProviderException.from(e);
            if (providerFailure != null) throw providerFailure;
            throw new BaseException(retryManaged
                    ? "AI 调用暂时失败，系统将自动重试"
                    : "AI 调用失败，本次配额已退回");
        }
    }

    private AiRuntimeConfig runtimeConfig() {
        return aiConfigService == null
                ? new AiRuntimeConfig(AiRuntimeConfigService.PROJECT, "env",
                        apiEndpoint, apiKey, model, "ENV")
                : aiConfigService.current(AiRuntimeConfigService.PROJECT);
    }

    private String buildPrompt(int phase, String summary, String extra, String previous) {
        return switch (phase) {
            case 1 -> """
                    请进入“需求理解”阶段，暂时不要实现代码。
                    项目概要：%s
                    附加信息：%s
                    输出：准确复述需求、核心模块、不确定点、MVP边界、推荐技术理解。
                    """.formatted(summary, value(extra));
            case 2 -> """
                    请进入“成功标准定义”阶段。
                    项目概要：%s
                    已确认的需求理解：%s
                    输出：总体MVP成功标准、分阶段标准、必须避免的回归、完成证据清单。每项必须可验证。
                    """.formatted(summary, value(previous));
            case 3 -> """
                    请进入“约束、风险与非目标”阶段。
                    项目概要：%s
                    前序内容：%s
                    输出：技术约束、业务约束、风险矩阵、非目标、冲突升级策略。
                    """.formatted(summary, value(previous));
            case 4 -> """
                    请生成可执行任务计划。
                    项目概要：%s
                    前序内容：%s
                    输出：当前基线、阶段拆解、详细步骤、文件清单、回滚策略和最终验收清单。
                    不要假装已经读取未提供的代码或执行过测试。
                    """.formatted(summary, value(previous));
            default -> throw new BaseException("无效的阶段编号");
        };
    }

    private String buildPromptLite(String summary, String previous) {
        return """
                基于前三阶段分析生成精简可执行任务计划。
                项目概要：%s
                前序分析：%s
                输出当前基线、4到6步任务、预计时间与里程碑、关键风险，控制在500字以内。
                """.formatted(summary, value(previous));
    }

    private String text(Object value, int max) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > max) throw new BaseException("输入内容过长");
        return result;
    }

    private int integer(Object value, String message) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { throw new BaseException(message); }
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value : "无";
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(240_000);
        return new RestTemplate(factory);
    }
}
