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
public class KnowledgeGraphService implements IKnowledgeGraphService {

    @Autowired
    private LeaderboardSnapshotMapper snapshotMapper;

    // Vibe Coding 关键词（AI 辅助编程相关）
    private static final String[] VC_KEYWORDS = {
        "vibe coding", "vibe-coding", "vibecoding",
        "ai coding", "ai-coding", "ai-coding",
        "cursor", "claude code", "claude-code",
        "copilot", "co-pilot",
        "ai-assisted", "ai assisted", "ai-powered",
        "llm", "agent", "agents", "ai agent",
        "code generation", "codegen",
        "ai ide", "ai editor",
        "prompt", "prompting",
        "chatgpt", "openai", "gpt-4", "gpt4",
        "deepseek", "anthropic",
        "vscode extension", "ai tool",
        "autonomous coding", "ai developer",
        "superpowers", "codex"
    };

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
            int vcCount = 0;
            long vcStars = 0;

            for (Map<String, Object> repo : repos) {
                Object starsObj = repo.get("stars");
                long stars = starsObj instanceof Number ? ((Number) starsObj).longValue() : 0;
                totalStars += stars;

                // Vibe Coding 标签
                String repoName = (String) repo.getOrDefault("name", "");
                String repoDesc = (String) repo.getOrDefault("description", "");
                String checkText = (repoName + " " + (repoDesc != null ? repoDesc : "")).toLowerCase();
                boolean isVC = false;
                for (String kw : VC_KEYWORDS) {
                    if (checkText.contains(kw)) { isVC = true; break; }
                }
                if (isVC) { vcCount++; vcStars += stars; }

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

            // 指标计算
            int repoCount = repos.size();
            long starsPerRepo = repoCount > 0 ? totalStars / repoCount : 0;
            int langDiversity = langCount.size();

            // 拥挤度
            String crowdedness = starsPerRepo > 10000 ? "拥挤" : starsPerRepo > 3000 ? "适中" : "蓝海";
            // 难度（基于语言多样性和平均stars）
            String difficulty = langDiversity > 5 ? "进阶" : langDiversity > 3 ? "中等" : "入门";
            if (starsPerRepo > 15000) difficulty = "进阶"; // 高星项目通常更复杂
            // 活跃度（基于最近push，快照中无此数据，用仓库数量代理）
            String activity = repoCount > 20 ? "活跃" : repoCount > 8 ? "一般" : "冷门";
            // 综合洞察
            String beginnerRepo = topRepos.isEmpty() ? "" : (String) topRepos.get(topRepos.size() - 1).get("name");
            String topRepo = topRepos.isEmpty() ? "" : (String) topRepos.get(0).get("name");
            // 生成话题简介
            String summary = generateTopicSummary(topicName, repos);
            // 本周数据稍后由 mergeWeeklyData 填充
            String insight = generateInsight(TOPIC_NAMES.getOrDefault(topicName, topicName), repoCount, totalStars, starsPerRepo, langDiversity, crowdedness, difficulty, beginnerRepo, topRepo, 0, 0, vcCount, repoCount > 0 ? vcCount * 100 / repoCount : 0);

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("crowdedness", crowdedness);
            metrics.put("difficulty", difficulty);
            metrics.put("activity", activity);
            metrics.put("starsPerRepo", starsPerRepo);
            metrics.put("langDiversity", langDiversity);
            metrics.put("insight", insight);
            metrics.put("summary", summary != null ? summary : "");
            metrics.put("vcCount", vcCount);
            metrics.put("vcStars", vcStars);
            metrics.put("vcRatio", repoCount > 0 ? vcCount * 100 / repoCount : 0);
            node.put("metrics", metrics);
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

    private String generateInsight(String name, int repoCount, long totalStars, long starsPerRepo,
                                     int langDiversity, String crowdedness, String difficulty,
                                     String beginnerRepo, String topRepo, int weeklyRepos, long weeklyStars,
                                     int vcCount, int vcRatio) {
        if (repoCount == 0) return "暂无足够数据进行分析";
        StringBuilder sb = new StringBuilder();

        // Vibe Coding 关联
        if (vcCount > 0) {
            sb.append("【Vibe Coding】该方向 ").append(repoCount).append(" 个热门项目中，").append(vcCount)
              .append(" 个（").append(vcRatio).append("%）与 AI 辅助编程直接相关。");
        }
        // 本周热度
        if (weeklyRepos > 0) {
            sb.append("【本周动态】本周 ").append(weeklyRepos).append(" 个仓库有新增 stars，周增 ").append(formatK(weeklyStars)).append("⭐。");
        } else {
            sb.append("【本周动态】暂无本周热门数据。");
        }
        sb.append(" ");

        // 拥挤度说明
        sb.append("【拥挤度：").append(crowdedness).append("】");
        sb.append("该方向共 ").append(repoCount).append(" 个热门仓库（全时段），均星 ").append(formatK(starsPerRepo)).append("。");
        if ("拥挤".equals(crowdedness)) {
            sb.append("头部项目（如 ").append(topRepo).append("）已达 ").append(formatK(totalStars)).append(" stars，竞争充分，建议从细分场景或垂直领域切入。");
        } else if ("适中".equals(crowdedness)) {
            sb.append("成熟度适中，头部效应未完全形成，仍有机会做出差异化产品。");
        } else {
            sb.append("仓库数量少、均星低，属于早期赛道，先行者有先发优势。");
        }
        sb.append(" ");

        // 难度说明
        sb.append("【难度：").append(difficulty).append("】");
        sb.append("该方向涉及 ").append(langDiversity).append(" 种主要编程语言");
        if ("进阶".equals(difficulty)) {
            sb.append("，且高星项目占比大，技术复杂度较高。建议先从 ");
            if (!beginnerRepo.isEmpty()) sb.append("「").append(beginnerRepo).append("」");
            else sb.append("低星入门项目");
            sb.append(" 开始学习。");
        } else if ("中等".equals(difficulty)) {
            sb.append("，有一定学习曲线但社区资源丰富。可参考 ");
            if (!topRepo.isEmpty()) sb.append("「").append(topRepo).append("」");
            sb.append(" 快速上手。");
        } else {
            sb.append("，技术栈相对集中，适合新手入门。推荐直接研究 ");
            if (!topRepo.isEmpty()) sb.append("「").append(topRepo).append("」");
            sb.append(" 的源码或文档。");
        }

        return sb.toString();
    }

    private static final Map<String, String> TOPIC_SUMMARIES = new LinkedHashMap<>();
    static {
        TOPIC_SUMMARIES.put("creative-coding", "利用代码生成视觉艺术、交互装置和动画的开源工具，涵盖生成艺术、着色器编程、音频可视化等创意技术方向。");
        TOPIC_SUMMARIES.put("game-development", "2D/3D 游戏引擎、图形渲染框架和游戏开发工具集，包括跨平台引擎、物理模拟、着色语言等。");
        TOPIC_SUMMARIES.put("WebGL", "基于浏览器的 3D 图形渲染库和框架，涵盖 WebGL 封装、科学可视化、地理信息展示和 GPU 加速计算。");
        TOPIC_SUMMARIES.put("Canvas", "HTML5 Canvas 2D 绘图库、图表可视化工具和数据驱动的图形渲染引擎，主要用于数据展示和交互式图形。");
        TOPIC_SUMMARIES.put("Web-Audio", "浏览器端音频处理库和音频可视化工具，支持音频播放、合成、效果处理和实时频谱分析。");
        TOPIC_SUMMARIES.put("ai-agents", "AI 智能体框架和工具链，利用大语言模型实现自动化任务执行、代码生成、多代理协作和复杂工作流编排。");
        TOPIC_SUMMARIES.put("RAG", "检索增强生成技术栈，将外部知识库与大语言模型结合，实现更准确的问答、文档理解和智能搜索。");
        TOPIC_SUMMARIES.put("self-hosted", "可私有部署的开源应用和服务，包括网络存储、媒体服务器、密码管理器、监控工具等，注重数据隐私和自主可控。");
        TOPIC_SUMMARIES.put("home-assistant", "智能家居开源平台及生态插件，支持多品牌设备接入、自动化场景编排和语音控制。");
        TOPIC_SUMMARIES.put("ESP32", "基于乐鑫 ESP32 芯片的嵌入式开发项目，涵盖物联网终端、传感器网络、智能家居控制和边缘 AI 推理。");
        TOPIC_SUMMARIES.put("raspberry-pi", "树莓派单板计算机相关的开源项目，包括操作系统、NAS 存储、GPIO 控制、家庭服务器和物联网网关。");
        TOPIC_SUMMARIES.put("home-automation", "家居自动化系统和协议实现，涵盖设备互联、场景联动、能源管理和安防监控等开源方案。");
        TOPIC_SUMMARIES.put("3d-printing", "3D 打印固件、切片软件和建模工具的开源实现，服务于从桌面级到工业级的增材制造需求。");
        TOPIC_SUMMARIES.put("other", "涵盖其他领域的热门开源项目，包括开发辅助工具、文档生成、知识管理和社区资源等。");
    }

    private String generateTopicSummary(String topicName, List<Map<String, Object>> repos) {
        return TOPIC_SUMMARIES.getOrDefault(topicName, "该方向暂无简介");
    }

    private String formatK(long n) {
        if (n >= 1000000) return String.format("%.1fM", n / 1000000.0);
        if (n >= 1000) return String.format("%.1fK", n / 1000.0);
        return String.valueOf(n);
    }

    // ---- 本周/上周对比 ----

    public Map<String, Object> buildCompareGraph() {
        LocalDate today = LocalDate.now();
        String latest = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String lastWeek = today.minusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 全时段数据
        Map<String, Object> graph1 = buildGraphWithFallback(latest);
        Map<String, Object> graph2 = buildGraphWithFallback(lastWeek);

        // 本周热门数据（week period）
        Map<String, Object> weeklyGraph = buildWeeklyGraph(latest);

        // 合并本周数据到 thisWeek 的节点中
        mergeWeeklyData(graph1, weeklyGraph);
        // 更新洞察文本（加入本周数据）
        refreshInsights(graph1);
        // 重新计算增长率（考虑本周活跃度）
        addGrowthAndScores(graph1, graph2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thisWeek", graph1);
        result.put("lastWeek", graph2);
        result.put("insights", buildInsightsReport(graph1));
        return result;
    }

    // 构建本周热门仓库图（period=week）
    public Map<String, Object> buildWeeklyGraph(String snapshotDate) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String topicName : TOPIC_NAMES.keySet()) {
            LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topicName, "week", snapshotDate);
            if (snap == null) { // fallback to yesterday
                LocalDate d = LocalDate.parse(snapshotDate, DateTimeFormatter.ISO_LOCAL_DATE);
                snap = snapshotMapper.selectByKey("github", topicName, "week", d.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            List<Map<String, Object>> repos = snap != null ? parseRepos(snap.getDataJson()) : Collections.emptyList();
            long weeklyStars = 0;
            for (Map<String, Object> repo : repos) {
                Object s = repo.get("stars");
                weeklyStars += s instanceof Number ? ((Number) s).longValue() : 0;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", topicName);
            node.put("weeklyStars", weeklyStars);
            node.put("weeklyRepos", repos.size());
            nodes.add(node);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void mergeWeeklyData(Map<String, Object> allTimeGraph, Map<String, Object> weeklyGraph) {
        List<Map<String, Object>> allTimeNodes = (List<Map<String, Object>>) allTimeGraph.get("nodes");
        List<Map<String, Object>> weeklyNodes = (List<Map<String, Object>>) weeklyGraph.get("nodes");
        if (allTimeNodes == null || weeklyNodes == null) return;

        Map<String, Map<String, Object>> weeklyMap = new LinkedHashMap<>();
        for (Map<String, Object> n : weeklyNodes) weeklyMap.put((String) n.get("id"), n);

        for (Map<String, Object> node : allTimeNodes) {
            Map<String, Object> wn = weeklyMap.get(node.get("id"));
            Map<String, Object> metrics = (Map<String, Object>) node.get("metrics");
            if (metrics == null) { metrics = new LinkedHashMap<>(); node.put("metrics", metrics); }
            if (wn != null) {
                metrics.put("weeklyStars", ((Number) wn.getOrDefault("weeklyStars", 0L)).longValue());
                metrics.put("weeklyRepos", ((Number) wn.getOrDefault("weeklyRepos", 0)).intValue());
            } else {
                metrics.put("weeklyStars", 0L);
                metrics.put("weeklyRepos", 0);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshInsights(Map<String, Object> graph) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        if (nodes == null) return;
        for (Map<String, Object> node : nodes) {
            String name = (String) node.get("name");
            int repoCount = ((Number) node.get("repoCount")).intValue();
            long totalStars = ((Number) node.get("totalStars")).longValue();
            Map<String, Object> metrics = (Map<String, Object>) node.get("metrics");
            if (metrics == null) continue;
            long starsPerRepo = ((Number) metrics.getOrDefault("starsPerRepo", 0L)).longValue();
            int langDiversity = ((Number) metrics.getOrDefault("langDiversity", 0)).intValue();
            String crowdedness = (String) metrics.get("crowdedness");
            String difficulty = (String) metrics.get("difficulty");
            int weeklyRepos = ((Number) metrics.getOrDefault("weeklyRepos", 0)).intValue();
            long weeklyStars = ((Number) metrics.getOrDefault("weeklyStars", 0L)).longValue();
            @SuppressWarnings("rawtypes")
            List topRepos = (List) node.get("topRepos");
            String beginnerRepo = (topRepos != null && !topRepos.isEmpty()) ? (String) ((Map) topRepos.get(topRepos.size() - 1)).get("name") : "";
            String topRepo = (topRepos != null && !topRepos.isEmpty()) ? (String) ((Map) topRepos.get(0)).get("name") : "";
            int vcCount2 = ((Number) metrics.getOrDefault("vcCount", 0)).intValue();
            int vcRatio2 = ((Number) metrics.getOrDefault("vcRatio", 0)).intValue();
            String insight = generateInsight(name, repoCount, totalStars, starsPerRepo,
                    langDiversity, crowdedness, difficulty, beginnerRepo, topRepo, weeklyRepos, weeklyStars, vcCount2, vcRatio2);
            metrics.put("insight", insight);
        }
    }

    @SuppressWarnings("unchecked")
    private void addGrowthAndScores(Map<String, Object> thisWeek, Map<String, Object> lastWeek) {
        List<Map<String, Object>> thisNodes = (List<Map<String, Object>>) thisWeek.get("nodes");
        List<Map<String, Object>> lastNodes = (List<Map<String, Object>>) lastWeek.get("nodes");
        if (thisNodes == null || lastNodes == null) return;

        Map<String, Long> lastStarMap = new LinkedHashMap<>();
        for (Map<String, Object> n : lastNodes) {
            lastStarMap.put((String) n.get("id"), ((Number) n.get("totalStars")).longValue());
        }

        // 找最大最小值用于标准化
        long maxStars = 0, maxGrowth = 0;
        for (Map<String, Object> n : thisNodes) {
            long stars = ((Number) n.get("totalStars")).longValue();
            if (stars > maxStars) maxStars = stars;
            Long last = lastStarMap.get(n.get("id"));
            if (last != null && last > 0) {
                long growth = Math.abs(stars - last);
                if (growth > maxGrowth) maxGrowth = growth;
            }
        }

        for (Map<String, Object> n : thisNodes) {
            long stars = ((Number) n.get("totalStars")).longValue();
            Map<String, Object> metrics = (Map<String, Object>) n.get("metrics");
            if (metrics == null) { metrics = new LinkedHashMap<>(); n.put("metrics", metrics); }

            // 增长率
            Long last = lastStarMap.get(n.get("id"));
            int growthPct = 0;
            if (last != null && last > 0) {
                growthPct = (int) Math.round((stars - last) * 100.0 / last);
            }
            metrics.put("growth", growthPct);
            n.put("growthPct", growthPct);

            // 综合评分 (0-100)
            double heatScore = maxStars > 0 ? (stars * 30.0 / maxStars) : 0;           // 热度 30%
            double growthScore = maxGrowth > 0 ? (Math.abs(growthPct) * 25.0 / Math.max(maxGrowth / Math.max(last != null ? last : 1, 1), 1)) : 0;
            growthScore = Math.min(25, growthPct > 0 ? growthPct * 0.5 : 0);          // 增长 25%
            long starsPerRepo = ((Number) metrics.getOrDefault("starsPerRepo", 0L)).longValue();
            double blueScore = starsPerRepo < 3000 ? 25 : starsPerRepo < 10000 ? 15 : 5; // 蓝海 25%
            int langDiv = ((Number) metrics.getOrDefault("langDiversity", 0)).intValue();
            double activeScore = langDiv > 5 ? 20 : langDiv > 3 ? 14 : 8;               // 活跃 20%

            int score = (int) Math.round(heatScore + growthScore + blueScore + activeScore);
            score = Math.min(100, Math.max(0, score));
            metrics.put("opportunityScore", score);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInsightsReport(Map<String, Object> graph) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        if (nodes == null) return Collections.emptyMap();

        // 热门 Top3
        List<Map<String, Object>> hot = new ArrayList<>(nodes);
        hot.sort((a, b) -> Long.compare(
            ((Number) b.get("totalStars")).longValue(),
            ((Number) a.get("totalStars")).longValue()));
        List<String> hotTop3 = hot.subList(0, Math.min(3, hot.size())).stream()
            .map(n -> (String) n.get("name")).collect(Collectors.toList());

        // 蓝海 Top3（stars/repo 最低）
        List<Map<String, Object>> blue = new ArrayList<>(nodes);
        blue.sort((a, b) -> {
            Map<String, Object> ma = (Map<String, Object>) a.get("metrics");
            Map<String, Object> mb = (Map<String, Object>) b.get("metrics");
            long sa = ma != null ? ((Number) ma.getOrDefault("starsPerRepo", Long.MAX_VALUE)).longValue() : Long.MAX_VALUE;
            long sb = mb != null ? ((Number) mb.getOrDefault("starsPerRepo", Long.MAX_VALUE)).longValue() : Long.MAX_VALUE;
            return Long.compare(sa, sb);
        });
        List<String> blueTop3 = blue.subList(0, Math.min(3, blue.size())).stream()
            .map(n -> (String) n.get("name")).collect(Collectors.toList());

        // 增长最快 Top3
        List<Map<String, Object>> growing = new ArrayList<>(nodes);
        growing.sort((a, b) -> {
            Map<String, Object> ma = (Map<String, Object>) a.get("metrics");
            Map<String, Object> mb = (Map<String, Object>) b.get("metrics");
            int ga = ma != null ? ((Number) ma.getOrDefault("growth", 0)).intValue() : 0;
            int gb = mb != null ? ((Number) mb.getOrDefault("growth", 0)).intValue() : 0;
            return Integer.compare(gb, ga);
        });
        List<String> growTop3 = growing.subList(0, Math.min(3, growing.size())).stream()
            .map(n -> (String) n.get("name")).collect(Collectors.toList());

        // 最佳机会 Top3（综合评分最高）
        List<Map<String, Object>> opp = new ArrayList<>(nodes);
        opp.sort((a, b) -> {
            Map<String, Object> ma = (Map<String, Object>) a.get("metrics");
            Map<String, Object> mb = (Map<String, Object>) b.get("metrics");
            int oa = ma != null ? ((Number) ma.getOrDefault("opportunityScore", 0)).intValue() : 0;
            int ob = mb != null ? ((Number) mb.getOrDefault("opportunityScore", 0)).intValue() : 0;
            return Integer.compare(ob, oa);
        });
        List<String> oppTop3 = opp.subList(0, Math.min(3, opp.size())).stream()
            .map(n -> (String) n.get("name")).collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hotTop3", hotTop3);
        report.put("blueTop3", blueTop3);
        report.put("growTop3", growTop3);
        report.put("oppTop3", oppTop3);
        return report;
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
