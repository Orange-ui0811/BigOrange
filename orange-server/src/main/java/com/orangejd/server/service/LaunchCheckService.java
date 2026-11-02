package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.LaunchAssessmentMapper;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LaunchCheckService {

    public static final int STAGE_COUNT = 8;
    public static final String PRICING_KEY = "ai:launch-check";

    private static final String[] STAGE_TITLES = {
            "", "部署形态评估", "资源选型与容量", "生产就绪审计", "部署实施方案",
            "上线前门禁", "测试与上线结论", "正式发布窗口", "持续运维方案"
    };

    private static final String[] STAGE_DESCRIPTIONS = {
            "",
            "判断本地、单机服务器、托管服务或云架构中最合适的部署形态",
            "给出服务器、数据库、Redis、对象存储和带宽的三档配置",
            "检查配置、迁移、文件、缓存、定时任务、安全和可观测性缺口",
            "生成可回滚的云资源准备、构建、上传、安装和数据迁移步骤",
            "检查域名、HTTPS、端口、账号、备份、告警、支付和合规门禁",
            "设计完整验收矩阵，并给出 GO、CONDITIONAL GO 或 NO-GO",
            "生成发布窗口、备份、哈希、迁移、冒烟、观察和回滚执行单",
            "制定巡检、监控、备份、扩容、故障、密钥轮换和成本管理方案"
    };

    private final LaunchAssessmentMapper mapper;
    private final QuotaService quotaService;
    private final PricingService pricingService;
    private final LaunchAssessmentReportService reportService;
    private final RestTemplate restTemplate;
    private final @org.springframework.lang.Nullable AiRuntimeConfigService aiConfigService;
    private final @org.springframework.lang.Nullable AiToolRouter aiToolRouter;

    @Value("${orange.ai-trend.endpoint:}")
    private String apiEndpoint;

    @Value("${orange.ai-trend.api-key:}")
    private String apiKey;

    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}") private String model;
    public LaunchCheckService(LaunchAssessmentMapper mapper, QuotaService quotaService,
            PricingService pricingService, LaunchAssessmentReportService reportService) {
        this(mapper, quotaService, pricingService, reportService, null, null); }
    @org.springframework.beans.factory.annotation.Autowired public LaunchCheckService(
            LaunchAssessmentMapper mapper, QuotaService quotaService, PricingService pricingService,
            LaunchAssessmentReportService reportService,
            @org.springframework.lang.Nullable AiRuntimeConfigService aiConfigService,
            @org.springframework.lang.Nullable AiToolRouter aiToolRouter) {
        this.mapper = mapper; this.quotaService = quotaService;
        this.pricingService = pricingService; this.reportService = reportService;
        this.aiConfigService = aiConfigService; this.aiToolRouter = aiToolRouter;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }
    public Map<String, Object> config() {
        List<Map<String, Object>> stages = new ArrayList<>();
        for (int i = 1; i <= STAGE_COUNT; i++) {
            stages.add(Map.of("stageNo", i, "title", STAGE_TITLES[i],
                    "description", STAGE_DESCRIPTIONS[i]));
        }
        return Map.of("stageCost", pricingService.getCost(PRICING_KEY), "stages", stages);
    }

    public Map<String, Object> create(Long accountId, Map<String, Object> body) {
        String projectName = text(body.get("projectName"), 128);
        String summary = text(body.get("projectSummary"), 10_000);
        if (!StringUtils.hasText(projectName)) throw new BaseException("请输入项目名称");
        if (summary.length() < 20) throw new BaseException("项目说明至少需要 20 个字");

        String repositoryUrl = text(body.get("repositoryUrl"), 512);
        if (StringUtils.hasText(repositoryUrl)) validateRepositoryUrl(repositoryUrl);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("accountId", accountId);
        assessment.put("projectName", projectName);
        assessment.put("repositoryUrl", emptyToNull(repositoryUrl));
        assessment.put("projectSummary", summary);
        assessment.put("targetAudience", emptyToNull(text(body.get("targetAudience"), 128)));
        assessment.put("budget", emptyToNull(text(body.get("budget"), 128)));
        assessment.put("region", emptyToNull(text(body.get("region"), 64)));
        assessment.put("expectedUsers", emptyToNull(text(body.get("expectedUsers"), 128)));
        assessment.put("uploadTypes", emptyToNull(text(body.get("uploadTypes"), 128)));
        assessment.put("paymentRequired", Boolean.TRUE.equals(body.get("paymentRequired")) ? 1 : 0);
        mapper.insert(assessment);
        return detail(accountId, ((Number) assessment.get("id")).longValue());
    }

    public List<Map<String, Object>> history(Long accountId) {
        return history(accountId, 30);
    }

    public List<Map<String, Object>> history(Long accountId, int limit) {
        return mapper.selectByAccountId(accountId, Math.max(1, Math.min(limit, 200)));
    }

    public Map<String, Object> detail(Long accountId, Long id) {
        Map<String, Object> assessment = requireOwned(accountId, id);
        assessment.put("stages", mapper.selectStages(id));
        return assessment;
    }

    public Map<String, Object> generate(Long accountId, Long id, int stageNo) {
        if (stageNo < 1 || stageNo > STAGE_COUNT) throw new BaseException("无效的体检阶段");
        Map<String, Object> assessment = requireOwned(accountId, id);
        if (stageNo > 1 && mapper.selectStage(id, stageNo - 1) == null) {
            throw new BaseException("请先完成上一阶段");
        }
        QuotaRequestIdentity identity = QuotaRequestIdentity.current(PRICING_KEY, accountId,
                Map.of("assessmentId", id, "stageNo", stageNo));
        String requestId = identity.requestId();
        if (aiToolRouter == null) {
            return generateWithRuntime(accountId, id, stageNo, assessment, identity, runtimeConfig());
        }
        AiToolContext context = new AiToolContext("launch-check.stage.generate", requestId,
                accountId, "LAUNCH_CHECK", id + ":" + stageNo, AiRuntimeConfigService.PROJECT,
                identity.requestHash());
        return aiToolRouter.execute(context, runtime ->
                generateWithRuntime(accountId, id, stageNo, assessment, identity, runtime));
    }

    private Map<String, Object> generateWithRuntime(Long accountId, Long id, int stageNo,
                                                    Map<String, Object> assessment,
                                                    QuotaRequestIdentity identity,
                                                    AiRuntimeConfig runtime) {
        if (!StringUtils.hasText(runtime.endpoint()) || !StringUtils.hasText(runtime.apiKey())) {
            throw new BaseException("AI 上线体检服务尚未配置");
        }

        QuotaCharge charge = quotaService.consume(accountId, PRICING_KEY,
                "上线体检 · " + STAGE_TITLES[stageNo], identity);
        int cost = Math.toIntExact(charge.amount());
        long balance = charge.balance();

        try {
            String prompt = LaunchCheckPromptFactory.build(assessment, mapper.selectStages(id), stageNo,
                    STAGE_TITLES[stageNo], STAGE_DESCRIPTIONS[stageNo]);
            String content = callAi(prompt, runtime);
            if (!StringUtils.hasText(content)) throw new IllegalStateException("AI 返回为空");

            reportService.saveStage(accountId, id, stageNo, STAGE_TITLES[stageNo], content, cost);

            return Map.of("stageNo", stageNo, "stageTitle", STAGE_TITLES[stageNo],
                    "content", content, "segmentCost", cost, "balance", balance);
        } catch (Exception e) {
            boolean retryManaged = aiToolRouter != null && AiToolRetryPolicy.isRetryable(e);
            if (!retryManaged) quotaService.refund(accountId, identity.requestId(), "上线体检失败退款");
            AiToolProviderException providerFailure = AiToolProviderException.from(e);
            if (providerFailure != null) throw providerFailure;
            throw new BaseException(retryManaged
                    ? "AI 生成暂时失败，系统将自动重试"
                    : "AI 生成失败，本次配额已退回");
        }
    }

    public void delete(Long accountId, Long id) {
        if (mapper.deleteOwned(id, accountId) == 0) throw new BaseException("报告不存在");
    }

    public Map<String, Object> adminPage(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return Map.of("summary", mapper.selectAdminStats(),
                "records", mapper.selectAdminPage((safePage - 1) * safeSize, safeSize));
    }

    public Map<String, Object> adminDetail(Long id) {
        Map<String, Object> assessment = mapper.selectAdminDetail(id);
        if (assessment == null) throw new BaseException("报告不存在");
        assessment.put("stages", mapper.selectStages(id));
        return assessment;
    }

    private Map<String, Object> requireOwned(Long accountId, Long id) {
        Map<String, Object> assessment = mapper.selectOwned(id, accountId);
        if (assessment == null) throw new BaseException("报告不存在或无权访问");
        return new LinkedHashMap<>(assessment);
    }

    @SuppressWarnings("unchecked")
    private String callAi(String prompt, AiRuntimeConfig runtime) {
        String url = runtime.endpoint().trim();
        if (!url.startsWith("http")) url = "https://" + url;
        if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(runtime.apiKey());

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "你是严谨的生产架构、测试、安全与运维专家。你的结论必须可验证，不能把健康检查等同于正式上线。"),
                Map.of("role", "user", "content", prompt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", runtime.model());
        body.put("messages", messages);
        body.put("temperature", 0.25);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        if (response.getBody() == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) return null;
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return message == null ? null : String.valueOf(message.getOrDefault("content", "")).trim();
    }

    private AiRuntimeConfig runtimeConfig() {
        return aiConfigService == null
                ? new AiRuntimeConfig(AiRuntimeConfigService.PROJECT, "env",
                        apiEndpoint, apiKey, model, "ENV")
                : aiConfigService.current(AiRuntimeConfigService.PROJECT);
    }

    private void validateRepositoryUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost().toLowerCase();
            if (!host.equals("github.com") && !host.equals("gitee.com")) {
                throw new BaseException("第一版仅支持 GitHub 或 Gitee 的 HTTPS 仓库地址");
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("代码仓库地址格式不正确");
        }
    }

    private String text(Object value, int maxLength) {
        String result = value == null ? "" : value.toString().trim();
        if (result.length() > maxLength) throw new BaseException("输入内容过长");
        return result;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

}
