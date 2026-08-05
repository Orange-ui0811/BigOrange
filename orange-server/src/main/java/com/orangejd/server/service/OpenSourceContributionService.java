package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.OpenSourceMapper;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import com.orangejd.server.foundation.aitool.AiToolContext;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import com.orangejd.server.foundation.aitool.AiToolRetryPolicy;
import com.orangejd.server.foundation.aitool.AiToolRouter;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OpenSourceContributionService {

    public static final String MATCH_PRICING_KEY = "ai:open-source-match";
    public static final String JOB_MATCH_PRICING_KEY = "ai:open-source-job-match";
    public static final String GUIDE_PRICING_KEY = "ai:open-source-guide";

    private final OpenSourceMapper mapper;
    private final OpenSourceGithubService githubService;
    private final OpenSourceTranslationService translationService;
    private final OpenSourceRefreshService refreshTask;
    private final OpenSourceAiClient aiClient;
    private final JobMatchService jobMatchService;
    private final PricingService pricingService;
    private final QuotaServiceImpl quotaService;
    private final ObjectMapper objectMapper;
    private final AiToolRouter aiToolRouter;
    public OpenSourceContributionService(OpenSourceMapper mapper, OpenSourceGithubService githubService,
            OpenSourceTranslationService translationService, OpenSourceRefreshService refreshTask,
            OpenSourceAiClient aiClient, JobMatchService jobMatchService, PricingService pricingService,
            QuotaServiceImpl quotaService, ObjectMapper objectMapper) {
        this(mapper, githubService, translationService, refreshTask, aiClient, jobMatchService,
                pricingService, quotaService, objectMapper, null); }
    @Autowired public OpenSourceContributionService(OpenSourceMapper mapper,
            OpenSourceGithubService githubService, OpenSourceTranslationService translationService,
            OpenSourceRefreshService refreshTask, OpenSourceAiClient aiClient,
            JobMatchService jobMatchService, PricingService pricingService,
            QuotaServiceImpl quotaService, ObjectMapper objectMapper,
            @org.springframework.lang.Nullable AiToolRouter aiToolRouter) {
        this.mapper = mapper; this.githubService = githubService;
        this.translationService = translationService; this.refreshTask = refreshTask;
        this.aiClient = aiClient; this.jobMatchService = jobMatchService;
        this.pricingService = pricingService; this.quotaService = quotaService;
        this.objectMapper = objectMapper; this.aiToolRouter = aiToolRouter;
    }

    public Map<String, Object> config() {
        return Map.of(
                "matchCost", pricingService.getCost(MATCH_PRICING_KEY),
                "jobMatchCost", pricingService.getCost(JOB_MATCH_PRICING_KEY),
                "guideCost", pricingService.getCost(GUIDE_PRICING_KEY),
                "difficulties", List.of("BEGINNER", "EASY", "MEDIUM"),
                "participationStatuses", List.of("TRY", "POSSIBLY_CLAIMED", "NOT_RECOMMENDED"));
    }

    public Map<String, Object> list(int page, int size, String difficulty, String participationStatus,
                                    String tech, String keyword, Integer updatedDays) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 40));
        Integer safeDays = updatedDays == null ? 30 : Math.max(1, Math.min(updatedDays, 90));
        List<Map<String, Object>> records = mapper.selectPublicIssues((safePage - 1) * safeSize, safeSize,
                normalizeEnum(difficulty), normalizeEnum(participationStatus), clean(tech, 64),
                clean(keyword, 100), safeDays);
        records.forEach(this::normalizeIssue);
        long total = mapper.countPublicIssues(normalizeEnum(difficulty), normalizeEnum(participationStatus),
                clean(tech, 64), clean(keyword, 100), safeDays);
        return Map.of("records", records, "total", total, "page", safePage, "size", safeSize);
    }

    public List<Map<String, Object>> ticker() {
        return mapper.selectTickerIssues(12);
    }

    public Map<String, Object> detail(Long id) {
        Map<String, Object> issue = mapper.selectIssueById(id);
        if (issue == null || !Boolean.TRUE.equals(asBoolean(issue.get("published")))) {
            throw new BaseException("Issue 不存在或已下架");
        }
        normalizeIssue(issue);
        return issue;
    }

    public Map<String, Object> verify(Long id) {
        Map<String, Object> issue = githubService.verifyIssue(id);
        normalizeIssue(issue);
        return issue;
    }

    public Map<String, Object> match(Long accountId, Map<String, Object> body) {
        String profile = clean(body.get("profile"), 2_000);
        String tech = clean(body.get("tech"), 64);
        String difficulty = normalizeEnum(clean(body.get("difficulty"), 32));
        String participation = normalizeEnum(clean(body.get("participationStatus"), 32));
        List<Long> selectedIds = selectedIssueIds(body.get("selectedIssueIds"));
        List<Map<String, Object>> candidates = selectedIds.size() >= 3
                ? mapper.selectIssuesByIds(selectedIds)
                : mapper.selectPublicIssues(0, 20, difficulty, participation, tech, null, 30);
        if (candidates.size() < 3) throw new BaseException("当前符合条件的 Issue 太少，请放宽筛选条件");

        StringBuilder context = new StringBuilder();
        Set<Long> allowedIds = new java.util.HashSet<>();
        for (Map<String, Object> issue : candidates) {
            long id = ((Number) issue.get("id")).longValue();
            allowedIds.add(id);
            context.append("\nID=").append(id)
                    .append(" | 标题=").append(issue.get("titleOriginal"))
                    .append(" | 摘要=").append(issue.get("summaryZh"))
                    .append(" | 技术=").append(issue.get("techStack"))
                    .append(" | 难度=").append(issue.get("difficulty"))
                    .append(" | 状态=").append(issue.get("participationStatus"))
                    .append(" | 评论=").append(issue.get("commentsCount"));
        }

        int cost = pricingService.getCost(MATCH_PRICING_KEY);
        String requestId = "opensource_match_" + accountId + "_" + UUID.randomUUID();
        if (aiToolRouter == null) {
            return matchWithRuntime(accountId, profile, context.toString(), allowedIds,
                    cost, requestId, null);
        }
        AiToolContext toolContext = new AiToolContext("open-source.issue.match", requestId,
                accountId, "OPEN_SOURCE_MATCH", null, AiRuntimeConfigService.CONTRIBUTION);
        return aiToolRouter.execute(toolContext, runtime -> matchWithRuntime(
                accountId, profile, context.toString(), allowedIds, cost, requestId, runtime));
    }

    private Map<String, Object> matchWithRuntime(Long accountId, String profile, String context,
                                                 Set<Long> allowedIds, int cost, String requestId,
                                                 AiRuntimeConfig runtime) {
        int balance = quotaService.consume(accountId, cost, "开源 Issue 智能匹配", requestId);
        try {
            String result = callAi(runtime, "你是谨慎的开源贡献机会顾问，只能从给定候选中选择。",
                    "用户条件：" + (StringUtils.hasText(profile) ? profile : "未补充")
                            + "\n候选 Issue：" + context
                            + "\n选择3到5条并推荐最容易完成的一条。严格输出JSON："
                            + "{\"recommendedIssueId\":1,\"matches\":[{\"issueId\":1,\"reason\":\"\","
                            + "\"risk\":\"\",\"difficulty\":\"入门/简单/中等\"}]}。不要输出候选外的ID。",
                    0.2);
            JsonNode root = objectMapper.readTree(result);
            List<Map<String, Object>> matches = new ArrayList<>();
            for (JsonNode item : root.path("matches")) {
                long issueId = item.path("issueId").asLong();
                if (!allowedIds.contains(issueId)) continue;
                Map<String, Object> issue = mapper.selectIssueById(issueId);
                if (issue == null) continue;
                normalizeIssue(issue);
                matches.add(Map.of("issue", issue,
                        "reason", item.path("reason").asText(""),
                        "risk", item.path("risk").asText(""),
                        "difficulty", item.path("difficulty").asText("")));
            }
            if (matches.size() < 3) throw new IllegalStateException("AI 返回的候选不足");
            long requestedRecommendedId = root.path("recommendedIssueId").asLong();
            boolean recommendationPresent = matches.stream().anyMatch(item ->
                    ((Number) ((Map<?, ?>) item.get("issue")).get("id")).longValue() == requestedRecommendedId);
            long recommendedId = recommendationPresent ? requestedRecommendedId
                    : ((Number) ((Map<?, ?>) matches.get(0).get("issue")).get("id")).longValue();
            return Map.of("recommendedIssueId", recommendedId,
                    "matches", matches, "segmentCost", cost, "balance", balance);
        } catch (Exception e) {
            boolean retryManaged = aiToolRouter != null && AiToolRetryPolicy.isRetryable(e);
            if (!retryManaged) quotaService.refund(accountId, cost, requestId, "开源 Issue 匹配失败退款");
            AiToolProviderException providerFailure = AiToolProviderException.from(e);
            if (providerFailure != null) throw providerFailure;
            throw new BaseException(retryManaged
                    ? "AI 匹配暂时失败，系统将自动重试"
                    : "AI 匹配失败，本次配额已退回");
        }
    }

    public Map<String, Object> jobMatch(Long accountId, Long issueId) {
        Map<String, Object> issue = requireIssue(issueId);
        String content = "开源仓库：" + issue.get("repositoryFullName")
                + "\nIssue：" + issue.get("titleOriginal")
                + "\n内容：" + abbreviate(String.valueOf(issue.getOrDefault("bodyOriginal", "")), 5_000)
                + "\n技术栈：" + issue.get("techStack");
        return jobMatchService.match(content, JOB_MATCH_PRICING_KEY, "开源 Issue 岗位匹配");
    }

    public Map<String, Object> generateGuide(Long accountId, Long issueId) {
        Map<String, Object> issue = requireIssue(issueId);
        int cost = pricingService.getCost(GUIDE_PRICING_KEY);
        String requestId = "opensource_guide_" + accountId + "_" + issueId + "_" + UUID.randomUUID();
        if (aiToolRouter == null) {
            return generateGuideWithRuntime(accountId, issueId, issue, cost, requestId, null);
        }
        AiToolContext context = new AiToolContext("open-source.guide.generate", requestId,
                accountId, "OPEN_SOURCE_GUIDE", String.valueOf(issueId),
                AiRuntimeConfigService.CONTRIBUTION);
        return aiToolRouter.execute(context, runtime ->
                generateGuideWithRuntime(accountId, issueId, issue, cost, requestId, runtime));
    }

    private Map<String, Object> generateGuideWithRuntime(Long accountId, Long issueId,
                                                         Map<String, Object> issue, int cost,
                                                         String requestId, AiRuntimeConfig runtime) {
        int balance = quotaService.consume(accountId, cost, "生成开源贡献指南", requestId);
        try {
            String prompt = "仓库：" + issue.get("repositoryFullName")
                    + "\nIssue：" + issue.get("titleOriginal")
                    + "\n正文：" + abbreviate(String.valueOf(issue.getOrDefault("bodyOriginal", "")), 8_000)
                    + "\n技术栈：" + issue.get("techStack")
                    + "\nNode：" + issue.get("nodeVersion") + "，包管理器：" + issue.get("packageManager")
                    + "\n贡献指南：" + issue.get("contributionUrl")
                    + "\n请输出1200到1800字中文Markdown，严格包含："
                    + "一、Issue要解决什么；二、开始前准备；三、推荐完成流程；四、验证清单；"
                    + "五、提交PR注意事项；六、结项准备清单。"
                    + "不要生成修复代码，不要虚构文件和测试结果；无法确认的内容明确标为需要用户核对。";
            String content = callAi(runtime,
                    "你是开源贡献导师，负责给出边界清晰、由用户自行执行的完成指南。",
                    prompt, 0.25);
            Map<String, Object> guide = new LinkedHashMap<>();
            guide.put("accountId", accountId);
            guide.put("issueId", issueId);
            guide.put("content", content);
            guide.put("segmentCost", cost);
            mapper.upsertGuide(guide);
            return Map.of("guide", guide, "balance", balance);
        } catch (Exception e) {
            boolean retryManaged = aiToolRouter != null && AiToolRetryPolicy.isRetryable(e);
            if (!retryManaged) quotaService.refund(accountId, cost, requestId, "开源贡献指南生成失败退款");
            AiToolProviderException providerFailure = AiToolProviderException.from(e);
            if (providerFailure != null) throw providerFailure;
            throw new BaseException(retryManaged
                    ? "指南生成暂时失败，系统将自动重试"
                    : "指南生成失败，本次配额已退回");
        }
    }

    private String callAi(AiRuntimeConfig runtime, String systemPrompt,
                          String userPrompt, double temperature) {
        return runtime == null
                ? aiClient.call(systemPrompt, userPrompt, temperature)
                : aiClient.call(runtime, systemPrompt, userPrompt, temperature);
    }

    public List<Map<String, Object>> guides(Long accountId) { return mapper.selectGuidesByAccount(accountId); }

    public Map<String, Object> guide(Long accountId, Long id) {
        Map<String, Object> guide = mapper.selectGuideOwned(id, accountId);
        if (guide == null) throw new BaseException("指南不存在");
        return guide;
    }

    public void deleteGuide(Long accountId, Long id) {
        if (mapper.deleteGuideOwned(id, accountId) != 1) {
            throw new BaseException("指南不存在");
        }
    }

    public Map<String, Object> adminOverview(int page, int size, String keyword,
                                             Integer published, String translationStatus) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return Map.of("repositories", mapper.selectRepositories(),
                "records", mapper.selectAdminIssues((safePage - 1) * safeSize, safeSize,
                        clean(keyword, 100), published, normalizeEnum(translationStatus)),
                "total", mapper.countAdminIssues(clean(keyword, 100), published, normalizeEnum(translationStatus)),
                "summary", mapper.selectAdminStats(), "refresh", refreshTask.status());
    }

    public Map<String, Object> saveRepository(Long id, Map<String, Object> body) {
        String fullName = clean(body.get("fullName"), 160);
        URI uri;
        try { uri = URI.create("https://github.com/" + fullName); }
        catch (Exception e) { throw new BaseException("仓库名称格式应为 owner/repository"); }
        if (!"github.com".equalsIgnoreCase(uri.getHost()) || fullName == null || !fullName.matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) {
            throw new BaseException("仓库名称格式应为 owner/repository");
        }
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put("id", id);
        repository.put("fullName", fullName);
        repository.put("displayName", clean(body.get("displayName"), 160));
        repository.put("organizationName", clean(body.get("organizationName"), 128));
        repository.put("htmlUrl", uri.toString());
        repository.put("tier", "CORE".equals(normalizeEnum(clean(body.get("tier"), 16))) ? "CORE" : "EXTENDED");
        repository.put("priority", Math.max(1, Math.min(number(body.get("priority"), 100), 9999)));
        repository.put("enabled", asBoolean(body.get("enabled")) ? 1 : 0);
        repository.put("techStack", clean(body.get("techStack"), 512));
        repository.put("systemRequirement", clean(body.get("systemRequirement"), 128));
        repository.put("memoryRequirement", clean(body.get("memoryRequirement"), 128));
        repository.put("diskRequirement", clean(body.get("diskRequirement"), 128));
        repository.put("environmentNotes", clean(body.get("environmentNotes"), 1000));
        if (id == null) mapper.insertRepository(repository); else mapper.updateRepository(repository);
        return mapper.selectRepositoryById(((Number) repository.get("id")).longValue());
    }

    public Map<String, Object> updateIssue(Long id, Map<String, Object> body) {
        Map<String, Object> issue = requireIssue(id);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", id);
        update.put("titleZh", valueOrExisting(body, "titleZh", issue, 500));
        update.put("summaryZh", valueOrExisting(body, "summaryZh", issue, 900));
        update.put("difficulty", allowed(clean(body.get("difficulty"), 16), Set.of("BEGINNER", "EASY", "MEDIUM"), text(issue.get("difficulty"))));
        update.put("participationStatus", allowed(clean(body.get("participationStatus"), 32),
                Set.of("TRY", "POSSIBLY_CLAIMED", "NOT_RECOMMENDED"), text(issue.get("participationStatus"))));
        update.put("published", body.containsKey("published") ? (asBoolean(body.get("published")) ? 1 : 0)
                : (asBoolean(issue.get("published")) ? 1 : 0));
        mapper.updateIssueAdmin(update);
        return mapper.selectIssueById(id);
    }

    public int refreshRepository(Long id) { return githubService.refreshRepository(id); }
    public void translateIssue(Long id) { translationService.translateOne(id); }
    public boolean triggerFullRefresh() { return refreshTask.triggerFullRefresh(); }
    public boolean triggerVerification() { return refreshTask.triggerVerification(); }

    public boolean triggerTranslation() { return refreshTask.triggerTranslation(); }

    private Map<String, Object> requireIssue(Long id) {
        Map<String, Object> issue = mapper.selectIssueById(id);
        if (issue == null) throw new BaseException("Issue 不存在");
        return issue;
    }

    private void normalizeIssue(Map<String, Object> issue) {
        issue.put("labels", parseJson(issue.remove("labelsJson")));
        issue.put("assignees", parseJson(issue.remove("assigneesJson")));
        issue.put("riskFlags", parseJson(issue.remove("riskFlagsJson")));
        if (!StringUtils.hasText(text(issue.get("titleZh")))) issue.put("titleZh", issue.get("titleOriginal"));
        if (!StringUtils.hasText(text(issue.get("summaryZh")))) issue.put("summaryZh", "中文摘要生成中，可先查看英文原文");
    }

    private Object parseJson(Object value) {
        if (value == null) return List.of();
        try { return objectMapper.readValue(String.valueOf(value), Object.class); }
        catch (Exception e) { return List.of(); }
    }

    private String valueOrExisting(Map<String, Object> body, String key, Map<String, Object> existing, int max) {
        return body.containsKey(key) ? clean(body.get(key), max) : text(existing.get(key));
    }
    private String allowed(String value, Set<String> allowed, String fallback) {
        String normalized = normalizeEnum(value);
        return allowed.contains(normalized) ? normalized : fallback;
    }
    private String normalizeEnum(String value) { return value == null ? null : value.trim().toUpperCase(); }
    private String clean(Object value, int max) {
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        if (result.isEmpty()) return null;
        return result.length() <= max ? result : result.substring(0, max);
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (Exception ignored) { return fallback; }
    }
    private List<Long> selectedIssueIds(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(item -> {
                    if (item instanceof Number number) return number.longValue();
                    try { return Long.parseLong(text(item)); } catch (Exception ignored) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(20)
                .toList();
    }
    private boolean asBoolean(Object value) {
        return Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value)
                || "1".equals(text(value)) || "true".equalsIgnoreCase(text(value));
    }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
