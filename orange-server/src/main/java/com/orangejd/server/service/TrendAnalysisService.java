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
    private static final int TIMEOUT_MS = 30000;

    @Autowired
    private KnowledgeGraphService graphService;

    private final RestTemplate restTemplate;

    public TrendAnalysisService() {
        this.restTemplate = new RestTemplate();
        // 30 秒超时
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(TIMEOUT_MS);
            setReadTimeout(TIMEOUT_MS);
        }});
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
                Long last = lastStars.get(n.get("id"));
                String delta = "";
                if (last != null && last > 0) {
                    double pct = (stars - last) * 100.0 / last;
                    delta = String.format(" (较上周 %+.0f%%)", pct);
                } else if (last == null) {
                    delta = " (上周无数据)";
                }

                sb.append(String.format("- **%s** [%s] | ⭐ %,d | 📦 %d repos%s\n",
                        name, group, stars, repos, delta));
            }
        }

        sb.append("\n请基于以上数据分析趋势，回答用户问题。");
        return sb.toString();
    }
}
