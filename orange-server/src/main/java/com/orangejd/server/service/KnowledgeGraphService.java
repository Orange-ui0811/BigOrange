package com.orangejd.server.service;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {

    @Autowired
    private LeaderboardSnapshotMapper snapshotMapper;

    // 15 个 topic 的中文名映射
    private static final Map<String, String> TOPIC_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> TOPIC_GROUPS = new LinkedHashMap<>();
    static {
        String[][] data = {
            {"creative-coding",    "创意编程",   "创意技术"},
            {"game-development",   "游戏开发",   "创意技术"},
            {"WebGL",              "WebGL",     "创意技术"},
            {"Canvas",             "Canvas",    "创意技术"},
            {"Web-Audio",          "Web音频",    "创意技术"},
            {"ai-agents",          "AI智能体",   "AI 智能"},
            {"RAG",                "RAG检索增强","AI 智能"},
            {"self-hosted",        "自托管",     "自托管"},
            {"home-assistant",     "家庭助理",   "自托管"},
            {"ESP32",              "ESP32",     "硬件/IoT"},
            {"raspberry-pi",       "树莓派",     "硬件/IoT"},
            {"home-automation",    "家居自动化", "硬件/IoT"},
            {"3d-printing",        "3D打印",    "硬件/IoT"},
            {"other",              "其他",      "其他"},
        };
        for (String[] d : data) {
            TOPIC_NAMES.put(d[0], d[1]);
            TOPIC_GROUPS.put(d[0], d[2]);
        }
    }

    // ---- 解析 snapshot data_json ----

    private List<Map<String, Object>> parseRepos(String dataJson) {
        List<Map<String, Object>> repos = new ArrayList<>();
        if (dataJson == null || dataJson.isEmpty()) return repos;
        try {
            // 提取 records 数组
            int arrStart = dataJson.indexOf("\"records\":[") + 11;
            if (arrStart < 11) return repos;
            int arrEnd = dataJson.lastIndexOf("]}");
            if (arrEnd < 0) arrEnd = dataJson.length() - 2;
            String arrStr = dataJson.substring(arrStart, arrEnd);

            // 解析 JSON 对象数组
            int depth = 0, objStart = -1;
            for (int i = 0; i < arrStr.length(); i++) {
                char c = arrStr.charAt(i);
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        String objStr = arrStr.substring(objStart + 1, i);
                        Map<String, Object> repo = parseJsonObj(objStr);
                        if (!repo.isEmpty()) repos.add(repo);
                    }
                }
            }
        } catch (Exception ignored) {}
        return repos;
    }

    private Map<String, Object> parseJsonObj(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        // 简单解析 key:value 对
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            int colon = pair.indexOf(":");
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
            String val = pair.substring(colon + 1).trim();
            if (val.startsWith("\"")) {
                val = val.substring(1, val.lastIndexOf("\""));
                map.put(key, val.replace("\\\"", "\"").replace("\\n", "\n"));
            } else if ("null".equals(val)) {
                map.put(key, null);
            } else if (val.contains(".")) {
                try { map.put(key, Double.parseDouble(val)); } catch (Exception e) { map.put(key, val); }
            } else {
                try { map.put(key, Long.parseLong(val)); } catch (Exception e) { map.put(key, val); }
            }
        }
        return map;
    }

    // ---- 构建图谱 ----

    public Map<String, Object> buildGraph(String snapshotDate) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 统计每个 topic 的语言分布（用于建边）
        Map<String, Map<String, Integer>> topicLanguages = new LinkedHashMap<>();

        for (String topicName : TOPIC_NAMES.keySet()) {
            LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topicName, "all", snapshotDate);
            List<Map<String, Object>> repos = snap != null ? parseRepos(snap.getDataJson()) : Collections.emptyList();

            long totalStars = 0;
            Map<String, Integer> langCount = new LinkedHashMap<>();
            List<Map<String, Object>> topRepos = new ArrayList<>();

            for (Map<String, Object> repo : repos) {
                Object starsObj = repo.get("stars");
                long stars = starsObj instanceof Number ? ((Number) starsObj).longValue() : 0;
                totalStars += stars;

                String lang = (String) repo.getOrDefault("language", "Other");
                if (lang == null || lang.isEmpty()) lang = "Other";
                langCount.merge(lang, 1, Integer::sum);

                if (topRepos.size() < 5) {
                    Map<String, Object> tr = new LinkedHashMap<>();
                    tr.put("name", repo.get("name"));
                    tr.put("stars", stars);
                    tr.put("htmlUrl", repo.get("htmlUrl"));
                    tr.put("description", repo.getOrDefault("description", ""));
                    topRepos.add(tr);
                }
            }

            topicLanguages.put(topicName, langCount);

            // 取 top 3 语言
            List<Map<String, Object>> topLanguages = langCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey()); m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList());

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", topicName);
            node.put("name", TOPIC_NAMES.getOrDefault(topicName, topicName));
            node.put("group", TOPIC_GROUPS.getOrDefault(topicName, "其他"));
            node.put("totalStars", totalStars);
            node.put("repoCount", repos.size());
            node.put("topLanguages", topLanguages);
            node.put("topRepos", topRepos);
            nodes.add(node);
        }

        // 构建边：共享语言数 ≥ 2
        List<String> topicList = new ArrayList<>(TOPIC_NAMES.keySet());
        for (int i = 0; i < topicList.size(); i++) {
            for (int j = i + 1; j < topicList.size(); j++) {
                String t1 = topicList.get(i), t2 = topicList.get(j);
                Map<String, Integer> l1 = topicLanguages.getOrDefault(t1, Collections.emptyMap());
                Map<String, Integer> l2 = topicLanguages.getOrDefault(t2, Collections.emptyMap());

                int shared = 0;
                for (String lang : l1.keySet()) {
                    if (l2.containsKey(lang)) shared++;
                }
                if (shared >= 2) {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", t1);
                    edge.put("target", t2);
                    edge.put("weight", shared);
                    edges.add(edge);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ---- 本周/上周对比 ----

    public Map<String, Object> buildCompareGraph() {
        // 取最新两个快照日期
        LocalDate today = LocalDate.now();
        String latest = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String lastWeek = today.minusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 尝试今天 → 昨天 → 上周 → 上上周
        Map<String, Object> graph1 = buildGraphWithFallback(latest);
        Map<String, Object> graph2 = buildGraphWithFallback(lastWeek);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thisWeek", graph1);
        result.put("lastWeek", graph2);
        return result;
    }

    private Map<String, Object> buildGraphWithFallback(String date) {
        Map<String, Object> graph = buildGraph(date);
        // 如果所有节点 totalStars 为 0，退回前一天
        if (allZero(graph)) {
            LocalDate d = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            for (int i = 1; i <= 7; i++) {
                String prevDate = d.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                graph = buildGraph(prevDate);
                if (!allZero(graph)) break;
            }
        }
        return graph;
    }

    @SuppressWarnings("unchecked")
    private boolean allZero(Map<String, Object> graph) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        if (nodes == null || nodes.isEmpty()) return true;
        for (Map<String, Object> n : nodes) {
            Object stars = n.get("totalStars");
            long s = stars instanceof Number ? ((Number) stars).longValue() : 0;
            if (s > 0) return false;
        }
        return true;
    }
}
