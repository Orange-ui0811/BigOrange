package com.orangejd.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TrendAnalysisService {

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
    public Map<String, Object> analyze(String apiEndpoint, String apiKey, String model, String question) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 获取双周图谱数据
        Map<String, Object> compareData;
        try {
            compareData = graphService.buildCompareGraph();
        } catch (Exception e) {
            result.put("error", "图谱数据获取失败: " + e.getMessage());
            return result;
        }

        // 格式化数据摘要
        String dataSummary = formatDataSummary(compareData);

        // 构建 OpenAI 兼容请求
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是开源趋势分析师。基于提供的真实 GitHub 数据（15 个技术方向的本周和上周热度数据），" +
                "分析 Vibe Coding（AI 辅助编程）在不同技术方向的发展趋势。" +
                "请严格遵守以下规则：\n" +
                "1. 只基于提供的数据分析，不编造不存在的数据\n" +
                "2. 如果数据不足以得出结论，明确告知\n" +
                "3. 用 Markdown 格式回复，包含标题、列表、加粗等\n" +
                "4. 给出具体的数据支撑（如 \"AI Agents 本周 48,892 stars，增长 12%\"）\n" +
                "5. 分析维度包括：热度排名、增长趋势、蓝海机会、难度评估、值得投入的方向");
        messages.add(systemMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", dataSummary + "\n\n用户问题: " + question);
        messages.add(userMsg);

        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);

        // 调大模型
        try {
            String url = apiEndpoint.trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            if (!url.endsWith("/chat/completions")) {
                url = url.replaceAll("/$", "") + "/chat/completions";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    result.put("content", message.getOrDefault("content", ""));
                }
                Map<String, Object> usage = (Map<String, Object>) response.getBody().get("usage");
                if (usage != null) {
                    result.put("usage", usage);
                }
            }
        } catch (Exception e) {
            log.warn("AI 分析请求失败: {}", e.getMessage().length() > 100 ? e.getMessage().substring(0, 100) : e.getMessage());
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("401") || msg.contains("403")) result.put("error", "API Key 无效或无权访问，请检查");
                else if (msg.contains("timeout") || msg.contains("read")) result.put("error", "请求超时（30s），请重试或换用更快的模型");
                else if (msg.contains("connect") || msg.contains("UnknownHost")) result.put("error", "无法连接到 " + apiEndpoint + "，请检查 Endpoint 地址");
                else result.put("error", "AI 模型调用失败: " + msg.substring(0, Math.min(100, msg.length())));
            } else {
                result.put("error", "AI 模型调用失败，请检查网络和配置");
            }
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
        "你是开源项目推荐专家。基于用户画像和 GitHub 真实数据，给出个人化项目推荐。\n\n" +
        "输出严格 JSON（不要 markdown 代码块，不要额外文字，不要省略字段）：\n" +
        "{\n" +
        "  \"analysis\": \"领域当前情况和市场分析（200字以内）\",\n" +
        "  \"recommendations\": [{\n" +
        "    \"name\": \"项目名\",\n" +
        "    \"description\": \"一句话介绍做什么\",\n" +
        "    \"difficulty\": \"入门/中等/进阶\",\n" +
        "    \"difficultyReason\": \"为什么是这个难度\",\n" +
        "    \"recommendReason\": \"结合用户画像说明推荐原因\",\n" +
        "    \"prerequisites\": \"需要学习的前置知识\",\n" +
        "    \"references\": [\"参考开源项目1\", \"参考开源项目2\"],\n" +
        "    \"score\": 85\n" +
        "  }],\n" +
        "  \"learning\": \"学习路径和预计出成果时间\",\n" +
        "  \"chartData\": [{\"label\": \"方向名\", \"score\": 85}]\n" +
        "}\n\n" +
        "recommendations 1-3 个，每个必含所有字段。score 0-100。图表 label 用中文方向名。" +
        "分析越详细越好，不要省略任何字段。";

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeV2(
            List<String> topics, String purpose, String experience, List<String> languages, String timeCommitment,
            String customTopics, String customPurpose, String customExp, String customLang, String customTime) {

        Map<String, Object> result = new LinkedHashMap<>();

        // 获取图谱数据
        Map<String, Object> compareData;
        try { compareData = graphService.buildCompareGraph(); }
        catch (Exception e) { result.put("error", "图谱数据获取失败"); return result; }

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
        String userPrompt = profile.toString() + "\n\n" + dataSummary +
            "\n\n请基于用户画像和数据，严格按 JSON 格式输出推荐结果。";

        // 调 AI
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", trendModel);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system"); sys.put("content", SYSTEM_PROMPT_V2);
            messages.add(sys);
            Map<String, String> usr = new LinkedHashMap<>();
            usr.put("role", "user"); usr.put("content", userPrompt);
            messages.add(usr);
            body.put("messages", messages);
            body.put("temperature", 0.5);

            String url = trendEndpoint.trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + trendApiKey);
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
            result.put("error", "AI分析失败: " + e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseV2Response(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isEmpty()) { result.put("error", "AI返回为空"); return result; }
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // 正则提取主要字段
        try {
            String analysis = regexExtract(content, "\"analysis\"\\s*:\\s*\"([^\"]+)\"");
            String learning = regexExtract(content, "\"learning\"\\s*:\\s*\"([^\"]+)\"");
            result.put("analysis", analysis != null ? analysis : "");
            result.put("learning", learning != null ? learning : "");
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
                    rec.put("recommendReason", regexExtract(obj, "\"recommendReason\"\\s*:\\s*\"([^\"]+)\""));
                    rec.put("prerequisites", regexExtract(obj, "\"prerequisites\"\\s*:\\s*\"([^\"]+)\""));
                    String scoreStr = regexExtract(obj, "\"score\"\\s*:\\s*(\\d+)");
                    rec.put("score", scoreStr != null ? Integer.parseInt(scoreStr) : 0);
                    // references 数组
                    String refsStr = regexExtract(obj, "\"references\"\\s*:\\s*\\[(.+?)\\]");
                    if (refsStr != null) {
                        List<String> refs = new ArrayList<>();
                        java.util.regex.Matcher rm = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(refsStr);
                        while (rm.find()) refs.add(rm.group(1));
                        rec.put("references", refs);
                    }
                    if (rec.get("name") != null) recs.add(rec);
                }
            }
            result.put("recommendations", recs);

            // chartData
            List<Map<String, Object>> chartData = new ArrayList<>();
            String chartStr = regexExtract(content, "\"chartData\"\\s*:\\s*\\[(.+?)\\]");
            if (chartStr != null) {
                java.util.regex.Matcher cm = java.util.regex.Pattern.compile("\"label\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"score\"\\s*:\\s*(\\d+)").matcher(chartStr);
                while (cm.find()) {
                    Map<String, Object> cd = new LinkedHashMap<>();
                    cd.put("label", cm.group(1));
                    cd.put("score", Integer.parseInt(cm.group(2)));
                    chartData.add(cd);
                }
            }
            result.put("chartData", chartData);
            return result;
        } catch (Exception e) {
            result.put("analysis", content.length() > 300 ? content.substring(0, 300) : content);
            result.put("recommendations", Collections.emptyList());
            result.put("chartData", Collections.emptyList());
            result.put("learning", "");
            result.put("raw", content);
            return result;
        }
    }

    private String regexExtract(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String nvl(String s) { return s == null || s.isEmpty() ? "未填写" : s; }
}
