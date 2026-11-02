package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class KnowledgeGraphComparisonService {
    private final LeaderboardSnapshotMapper snapshotMapper;
    private final KnowledgeSnapshotParser parser;
    private final KnowledgeGraphAssembler assembler;

    KnowledgeGraphComparisonService(LeaderboardSnapshotMapper snapshotMapper,
                                    KnowledgeSnapshotParser parser,
                                    KnowledgeGraphAssembler assembler) {
        this.snapshotMapper = snapshotMapper;
        this.parser = parser;
        this.assembler = assembler;
    }

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

public Map<String, Object> buildWeeklyGraph(String snapshotDate) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String topicName : KnowledgeGraphCatalog.TOPIC_NAMES.keySet()) {
            LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topicName, "week", snapshotDate);
            if (snap == null) { // fallback to yesterday
                LocalDate d = LocalDate.parse(snapshotDate, DateTimeFormatter.ISO_LOCAL_DATE);
                snap = snapshotMapper.selectByKey("github", topicName, "week", d.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            List<Map<String, Object>> repos = snap != null ? parser.parseRepos(snap.getDataJson()) : Collections.emptyList();
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
            String insight = assembler.generateInsight(name, repoCount, totalStars, starsPerRepo,
                    langDiversity, crowdedness, difficulty, beginnerRepo, topRepo, weeklyRepos, weeklyStars, vcCount2, vcRatio2);
            metrics.put("insight", insight);
        }
    }

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
        Map<String, Object> graph = assembler.buildGraph(date);
        // Prefer nearby snapshots, then use the newest complete data set available.
        if (allZero(graph)) {
            LocalDate d = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            for (int i = 1; i <= 7; i++) {
                String prevDate = d.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                graph = assembler.buildGraph(prevDate);
                if (!allZero(graph)) break;
            }
        }
        if (allZero(graph)) {
            graph = buildLatestGraph();
        }
        return graph;
    }

private Map<String, Object> buildLatestGraph() {
        return assembler.buildGraph("__LATEST__");
    }

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

