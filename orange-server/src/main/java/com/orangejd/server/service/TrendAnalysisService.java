package com.orangejd.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TrendAnalysisService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.orangejd.server.foundation.external.AiRuntimeConfigService aiConfigService;

    private static final Logger log = LoggerFactory.getLogger(TrendAnalysisService.class);
    private static final int TIMEOUT_MS = 90000;

    @Autowired
    private KnowledgeGraphService graphService;

    @org.springframework.beans.factory.annotation.Value("${orange.ai-trend.endpoint:}")
    private String trendEndpoint;

    @org.springframework.beans.factory.annotation.Value("${orange.ai-trend.api-key:}")
    private String trendApiKey;

    @org.springframework.beans.factory.annotation.Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String trendModel;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private PricingService pricingService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final RestTemplate restTemplate;
    private final RestTemplate trendRestTemplate;

    public TrendAnalysisService() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(TIMEOUT_MS);
            setReadTimeout(TIMEOUT_MS);
        }});
        // 趋势分析不设超时
        this.trendRestTemplate = new RestTemplate();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(String question) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 配额消费
        Long accountId = com.orangejd.common.context.BaseContext.getCurrentId();
        String requestId = null;
        int chargedCost = 0;
        if (accountId != null && accountId > 0) {
            requestId = "trend_v1_" + accountId + "_" + System.currentTimeMillis();
            try { chargedCost = pricingService.getCost("ai:trend:v1"); quotaService.consume(accountId, chargedCost, "趋势分析 V1", requestId); }
            catch (Exception e) { result.put("error", e.getMessage()); return result; }
        }

        // 获取双周图谱数据
        Map<String, Object> compareData;
        try { compareData = graphService.buildCompareGraph(); }
        catch (Exception e) {
            if (chargedCost > 0) quotaService.refund(accountId, chargedCost, requestId, "图谱获取失败退款");
            result.put("error", "图谱数据获取失败"); return result;
        }

        // 格式化数据摘要
        String dataSummary = formatDataSummary(compareData);

        // RAG 检索
        String ragContext = "";
        if (vectorStore != null) {
            try {
                List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder().query(question).topK(2).similarityThreshold(0.5).build());
                if (!docs.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < docs.size(); i++) {
                        sb.append("参考资料").append(i+1).append("：").append(docs.get(i).getText()).append("\n");
                    }
                    ragContext = sb.toString();
                }
            } catch (Exception ignored) {}
        }

        String userPrompt = dataSummary + (ragContext.isEmpty() ? "" : "\n\n参考资料：\n" + ragContext) +
                "\n\n用户问题: " + question;

        // 构建请求（使用服务端配置）
        com.orangejd.server.foundation.external.AiRuntimeConfig runtime =
                projectRuntimeConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", runtime.model());
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", "你是开源趋势分析师。基于提供的真实 GitHub 数据和知识库参考资料，分析技术趋势。用 Markdown 格式回复。");
        messages.add(sys);
        Map<String, String> usr = new LinkedHashMap<>();
        usr.put("role", "user"); usr.put("content", userPrompt);
        messages.add(usr);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);

        // 调大模型
        try {
            String url = runtime.endpoint().trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + runtime.apiKey());
            ResponseEntity<Map> response = trendRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class);
            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    result.put("content", message.getOrDefault("content", ""));
                    result.put("balance", accountId != null ? quotaService.getBalance(accountId) : 0);
                }
            }
        } catch (Exception e) {
            log.warn("AI趋势分析V1失败: {}", e.getMessage());
            if (chargedCost > 0) quotaService.refund(accountId, chargedCost, requestId, "AI分析失败退款");
            result.put("error", "AI分析失败，请稍后重试");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String formatDataSummary(Map<String, Object> compareData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 本周 GitHub 热度数据（按 topic 分类）\n\n");

        Map<String, Object> thisWeek = (Map<String, Object>) compareData.get("thisWeek");
        Map<String, Object> lastWeek = (Map<String, Object>) compareData.get("lastWeek");

        List<Map<String, Object>> thisNodes = (List<Map<String, Object>>) thisWeek.get("nodes");
        List<Map<String, Object>> lastNodes = (List<Map<String, Object>>) lastWeek.get("nodes");

        // 建立上周查找
        Map<String, Long> lastStars = new LinkedHashMap<>();
        if (lastNodes != null) {
            for (Map<String, Object> n : lastNodes) {
                lastStars.put((String) n.get("id"), ((Number) n.get("totalStars")).longValue());
            }
        }

        if (thisNodes != null) {
            for (Map<String, Object> n : thisNodes) {
                String name = (String) n.get("name");
                long stars = ((Number) n.get("totalStars")).longValue();
                int repos = ((Number) n.get("repoCount")).intValue();
                String group = (String) n.get("group");
                Map<String, Object> metrics = (Map<String, Object>) n.get("metrics");
                String crowdedness = metrics != null ? (String) metrics.getOrDefault("crowdedness", "") : "";
                String difficulty = metrics != null ? (String) metrics.getOrDefault("difficulty", "") : "";
                int score = metrics != null ? ((Number) metrics.getOrDefault("opportunityScore", 0)).intValue() : 0;
                int growth = metrics != null ? ((Number) metrics.getOrDefault("growth", 0)).intValue() : 0;
                String insight = metrics != null ? (String) metrics.getOrDefault("insight", "") : "";
                Long last = lastStars.get(n.get("id"));
                String delta = "";
                if (last != null && last > 0) {
                    delta = String.format(" (较上周 %+d%%)", growth);
                } else if (last == null) {
                    delta = " (上周无数据)";
                }

                sb.append(String.format("- **%s** [%s] | ⭐ %,d | 📦 %d repos%s | 拥挤度: %s | 难度: %s | 综合评分: %d/100\n  洞察: %s\n",
                        name, group, stars, repos, delta, crowdedness, difficulty, score, insight));
            }
        }

        sb.append("\n请基于以上数据分析趋势，回答用户问题。");
        return sb.toString();
    }

    // ==================== V2: 四维画像分析 ====================

    private static final String SYSTEM_PROMPT_V2 =
        "你是 Vibe Coding（AI 辅助编程）项目推荐专家。Vibe Coding 指利用 AI（如 Claude、Cursor、Copilot、ChatGPT 等）辅助编写代码的新型开发方式。" +
        "你的任务是推荐适合通过 Vibe Coding 方式参与或学习的开源项目。" +
        "基于用户画像和 GitHub 真实数据，给出详细个人化 Vibe Coding 项目推荐。\n\n" +
        "输出严格 JSON（不要 markdown 代码块，不要额外文字）：\n" +
        "{\n" +
        "  \"analysis\": {\n" +
        "    \"trend\": \"当前发展趋势（增长还是稳定还是衰退，增长速度如何）\",\n" +
        "    \"prospect\": \"发展前景（未来1-2年的机会和方向）\",\n" +
        "    \"demand\": \"市场需求（企业招聘、商业应用、社区活跃度）\",\n" +
        "    \"summary\": \"一句话总结（50字）\"\n" +
        "  },\n" +
        "  \"recommendations\": [{\n" +
        "    \"name\": \"项目名称（用中文描述性名称，绝对不要用GitHub仓库英文原名。如推荐dify要说'开源LLM应用开发平台'而非'dify'，推荐langchain要说'大语言模型应用框架'而非'langchain'）\",\n" +
        "    \"description\": \"详细介绍这个项目是什么、解决什么问题、核心功能（80字以上）\",\n" +
        "    \"difficulty\": \"入门/中等/进阶\",\n" +
        "    \"difficultyReason\": \"为什么是这个难度（结合用户的技术基础分析）\",\n" +
        "    \"vibeCodingReason\": \"为什么这个项目适合 Vibe Coding（AI 辅助编程方式参与）\",\n" +
        "    \"recommendReason\": \"推荐原因（100字以上）。必须结合用户的目的（学习/求职/赚钱/兴趣）详细分析：求职目的→该领域面试官看重什么能力、这个项目能证明什么；学习目的→学习路径和成长价值；赚钱目的→商业价值和变现方式。其中针对目的的专门分析不少于50字\",\n" +
        "    \"prerequisites\": \"学习该项目需要的前置知识和技能\",\n" +
        "    \"references\": [{\"name\": \"参考项目英文名\", \"url\": \"https://github.com/xxx\"}],\n" +
        "    \"score\": 85\n" +
        "  }],\n" +
        "  \"learning\": {\n" +
        "    \"steps\": [\"第一步做什么\", \"第二步做什么\", \"第三步做什么\"],\n" +
        "    \"timeline\": \"预计多长时间能看到成果\",\n" +
        "    \"tips\": \"学习过程中的建议\"\n" +
        "  }\n" +
        "}\n\n" +
        "每个字段都要详细写，不要省略，不要简写。recommendations 1-3 个。analysis 四个子字段必填。" +
        "description 至少 80 字。recommendReason 至少 60 字。";

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeV2(
            List<String> topics, String purpose, String experience, List<String> languages, String timeCommitment,
            String customTopics, String customPurpose, String customExp, String customLang, String customTime,
            Boolean useRag) {

        Map<String, Object> result = new LinkedHashMap<>();

        // 配额消费
        Long accountId = com.orangejd.common.context.BaseContext.getCurrentId();
        String requestId = null;
        boolean consumed = false;
        int chargedCost = 0;
        if (accountId != null && accountId > 0) {
            requestId = "trend_v2_" + accountId + "_" + System.currentTimeMillis();
            try {
                chargedCost = pricingService.getCost("ai:trend:v2");
                int balance = quotaService.consume(accountId, chargedCost, "趋势分析 V2", requestId);
                consumed = true;
                result.put("balance", balance);
                log.info("[QUOTA] 趋势分析预扣: accountId={}, balance={}", accountId, balance);
            } catch (Exception e) {
                result.put("error", e.getMessage());
                return result;
            }
        }

        // 获取图谱数据
        Map<String, Object> compareData;
        try { compareData = graphService.buildCompareGraph(); }
        catch (Exception e) {
            if (consumed) quotaService.refund(accountId, chargedCost, requestId, "图谱获取失败退款");
            result.put("error", "图谱数据获取失败"); return result;
        }

        // 构建用户画像
        StringBuilder profile = new StringBuilder();
        profile.append("## 用户画像\n\n");
        if (topics != null && !topics.isEmpty())
            profile.append("- 感兴趣方向: ").append(String.join("、", topics));
        if (customTopics != null && !customTopics.isEmpty())
            profile.append("（").append(customTopics).append("）");
        profile.append("\n");
        profile.append("- 目的: ").append(nvl(purpose));
        if (customPurpose != null && !customPurpose.isEmpty()) profile.append("（").append(customPurpose).append("）");
        profile.append("\n");
        profile.append("- 编程经验: ").append(nvl(experience));
        if (customExp != null && !customExp.isEmpty()) profile.append("（").append(customExp).append("）");
        profile.append("\n");
        if (languages != null && !languages.isEmpty())
            profile.append("- 掌握语言: ").append(String.join("、", languages));
        if (customLang != null && !customLang.isEmpty()) profile.append("（").append(customLang).append("）");
        profile.append("\n");
        profile.append("- 时间投入: ").append(nvl(timeCommitment));
        if (customTime != null && !customTime.isEmpty()) profile.append("（").append(customTime).append("）");
        profile.append("\n");

        // 数据摘要
        String dataSummary = formatDataSummary(compareData);

        // ---- RAG 检索增强 ----
        String ragContext = (useRag != null && useRag)
            ? buildRagContext(topics, purpose, experience, timeCommitment,
                customTopics, customPurpose, customExp, customTime)
            : "";

        String userPrompt = profile.toString() + "\n\n" + dataSummary +
            (ragContext.isEmpty() ? "" : "\n\n## 知识库参考\n" + ragContext) +
            "\n\n请基于用户画像、数据和知识库参考，严格按 JSON 格式输出推荐结果。";

        // 调 AI
        try {
            com.orangejd.server.foundation.external.AiRuntimeConfig runtime = projectRuntimeConfig();
            String endpoint = runtime.endpoint();
            String apiKey = runtime.apiKey();
            String model = runtime.model();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system"); sys.put("content", SYSTEM_PROMPT_V2);
            messages.add(sys);
            Map<String, String> usr = new LinkedHashMap<>();
            usr.put("role", "user"); usr.put("content", userPrompt);
            messages.add(usr);
            body.put("messages", messages);
            body.put("temperature", 0.5);

            String url = endpoint.trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = trendRestTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (resp.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) msg.getOrDefault("content", "");
                    result = parseV2Response(content);
                }
            }
        } catch (Exception e) {
            log.warn("AI趋势分析V2失败: {}", e.getMessage());
            if (consumed) {
                quotaService.refund(accountId, chargedCost, requestId, "AI分析失败退款");
                log.info("[QUOTA] 退款: accountId={}, amount={}", accountId, chargedCost);
            }
            result.put("error", "AI分析失败，请稍后重试");
        }
        return result;
    }

    private com.orangejd.server.foundation.external.AiRuntimeConfig projectRuntimeConfig() {
        return aiConfigService == null
                ? new com.orangejd.server.foundation.external.AiRuntimeConfig(
                        com.orangejd.server.foundation.external.AiRuntimeConfigService.PROJECT,
                        "env", trendEndpoint, trendApiKey, trendModel, "ENV")
                : aiConfigService.current(
                        com.orangejd.server.foundation.external.AiRuntimeConfigService.PROJECT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseV2Response(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isEmpty()) { result.put("error", "AI返回为空"); return result; }
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // 正则提取主要字段
        try {
            // analysis 现在是一个对象
            Map<String, Object> analysisObj = new LinkedHashMap<>();
            analysisObj.put("trend", regexExtract(content, "\"trend\"\\s*:\\s*\"([^\"]+)\""));
            analysisObj.put("prospect", regexExtract(content, "\"prospect\"\\s*:\\s*\"([^\"]+)\""));
            analysisObj.put("demand", regexExtract(content, "\"demand\"\\s*:\\s*\"([^\"]+)\""));
            analysisObj.put("summary", regexExtract(content, "\"summary\"\\s*:\\s*\"([^\"]+)\""));
            result.put("analysis", analysisObj);

            // learning 现在是一个对象
            Map<String, Object> learningObj = new LinkedHashMap<>();
            learningObj.put("timeline", regexExtract(content, "\"timeline\"\\s*:\\s*\"([^\"]+)\""));
            learningObj.put("tips", regexExtract(content, "\"tips\"\\s*:\\s*\"([^\"]+)\""));
            // steps 数组
            String stepsStr = regexExtract(content, "\"steps\"\\s*:\\s*\\[(.+?)\\]");
            if (stepsStr != null) {
                List<String> steps = new ArrayList<>();
                java.util.regex.Matcher sm = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(stepsStr);
                while (sm.find()) steps.add(sm.group(1));
                learningObj.put("steps", steps);
            }
            result.put("learning", learningObj);
            result.put("raw", content);

            // 提取 recommendations 数组
            List<Map<String, Object>> recs = new ArrayList<>();
            String recsStr = regexExtract(content, "\"recommendations\"\\s*:\\s*\\[(.+?)\\]\\s*,\\s*\"learning\"");
            if (recsStr == null) recsStr = regexExtract(content, "\"recommendations\"\\s*:\\s*\\[(.+?)\\]");
            if (recsStr != null) {
                // 分割每个 JSON 对象
                String[] objs = recsStr.split("\\},\\s*\\{");
                for (String obj : objs) {
                    obj = obj.replaceAll("^\\s*\\{?", "{").replaceAll("\\}?\\s*$", "}");
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("name", regexExtract(obj, "\"name\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("description", regexExtract(obj, "\"description\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("difficulty", regexExtract(obj, "\"difficulty\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("difficultyReason", regexExtract(obj, "\"difficultyReason\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("vibeCodingReason", regexExtract(obj, "\"vibeCodingReason\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("recommendReason", regexExtract(obj, "\"recommendReason\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("prerequisites", regexExtract(obj, "\"prerequisites\"\\s*:\\s*\"([^\"]+)\""));
                    String scoreStr = regexExtract(obj, "\"score\"\\s*:\\s*(\\d+)");
                    rec.put("score", scoreStr != null ? Integer.parseInt(scoreStr) : 0);
                    // references 支持新旧两种格式
                    String refsStr = regexExtract(obj, "\"references\"\\s*:\\s*\\[(.+?)\\]");
                    if (refsStr != null) {
                        List<Object> refs = new ArrayList<>();
                        // 新格式: {name, url}
                        java.util.regex.Matcher rmObj = java.util.regex.Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*\\}").matcher(refsStr);
                        if (rmObj.find()) {
                            rmObj.reset();
                            while (rmObj.find()) {
                                Map<String, String> ref = new LinkedHashMap<>();
                                ref.put("name", rmObj.group(1));
                                ref.put("url", rmObj.group(2));
                                refs.add(ref);
                            }
                        } else {
                            // 旧格式: 纯字符串数组
                            java.util.regex.Matcher rmStr = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(refsStr);
                            while (rmStr.find()) { String s = rmStr.group(1); if (!s.startsWith("http")) refs.add(s); }
                        }
                        if (!refs.isEmpty()) rec.put("references", refs);
                    }
                    if (rec.get("name") != null) recs.add(rec);
                }
            }
            result.put("recommendations", recs);

            return result;
        } catch (Exception e) {
            result.put("analysis", content.length() > 300 ? content.substring(0, 300) : content);
            result.put("recommendations", Collections.emptyList());
            result.put("learning", new LinkedHashMap<>());
            result.put("raw", content);
            return result;
        }
    }

    private String regexExtract(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String nvl(String s) { return s == null || s.isEmpty() ? "未填写" : s; }

    private String getRedisOrDefault(String key, String fallback) {
        if (redisTemplate == null) return fallback;
        String v = redisTemplate.opsForValue().get(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    // ==================== RAG 分维检索 ====================

    /**
     * 分维检索知识库：画像拆成 4 条 query，各自检索 Top 2，合并去重取 Top 3
     */
    private String buildRagContext(List<String> topics, String purpose, String experience,
            String timeCommitment, String customTopics, String customPurpose,
            String customExp, String customTime) {
        if (vectorStore == null) return "";

        try {
            List<String> queries = new ArrayList<>();

            // ① 方向 query
            StringBuilder dirQuery = new StringBuilder("技术方向：");
            if (topics != null && !topics.isEmpty())
                dirQuery.append(String.join(" ", topics)).append(" 开源项目入门实践");
            if (customTopics != null && !customTopics.isEmpty())
                dirQuery.append(" ").append(customTopics);
            queries.add(dirQuery.toString());

            // ② 目的 query
            StringBuilder purposeQuery = new StringBuilder("技术学习目的：").append(nvl(purpose));
            if (customPurpose != null && !customPurpose.isEmpty())
                purposeQuery.append(" ").append(customPurpose);
            purposeQuery.append(" ").append(purposeToSearchContext(nvl(purpose)));
            queries.add(purposeQuery.toString());

            // ③ 基础 query
            StringBuilder expQuery = new StringBuilder("编程经验：").append(nvl(experience));
            if (customExp != null && !customExp.isEmpty())
                expQuery.append(" ").append(customExp);
            expQuery.append(" 学习路径入门");
            queries.add(expQuery.toString());

            // ④ 时间 query
            StringBuilder timeQuery = new StringBuilder("时间投入：").append(nvl(timeCommitment));
            if (customTime != null && !customTime.isEmpty())
                timeQuery.append(" ").append(customTime);
            timeQuery.append(" 学习计划 时间管理");
            queries.add(timeQuery.toString());

            // ② 每条 query 检索 Top 2
            Map<String, Document> seen = new LinkedHashMap<>(); // textHash → Document
            for (String q : queries) {
                try {
                    List<Document> hits = vectorStore.similaritySearch(
                        SearchRequest.builder().query(q).topK(2).similarityThreshold(0.6).build()
                    );
                    for (Document doc : hits) {
                        String key = doc.getText().substring(0, Math.min(50, doc.getText().length()));
                        seen.putIfAbsent(key, doc);
                    }
                } catch (Exception e) {
                    log.debug("[RAG] 单维度检索异常: {}", e.getMessage());
                }
            }
            log.info("[RAG] 分维检索: 4 queries → {} unique fragments", seen.size());

            // ③ 取 Top 3
            List<Document> selected = new ArrayList<>(seen.values());
            if (selected.size() > 3) selected = selected.subList(0, 3);

            // ④ 格式化为 prompt 片段
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selected.size(); i++) {
                Document doc = selected.get(i);
                sb.append("【片段").append(i + 1).append("】");
                Object title = doc.getMetadata().get("docTitle");
                if (title != null) sb.append(" 来源：").append(title);
                sb.append("\n").append(doc.getText()).append("\n\n");
            }
            log.info("[RAG] prompt 已拼接, 知识库片段数={}", selected.size());
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG] 检索失败，跳过增强: {}", e.getMessage());
            return "";
        }
    }

    private String purposeToSearchContext(String purpose) {
        if (purpose.contains("求职")) return "面试技巧 项目经验 GitHub作品集";
        if (purpose.contains("学习")) return "学习路径 入门教程 从零开始";
        if (purpose.contains("赚钱")) return "商业变现 开源项目商业化";
        if (purpose.contains("探索")) return "技术趋势 新兴方向 兴趣探索";
        return "";
    }
}
