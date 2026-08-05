package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class KnowledgeGraphAssembler {
    private final LeaderboardSnapshotMapper snapshotMapper;
    private final KnowledgeSnapshotParser parser;

    KnowledgeGraphAssembler(LeaderboardSnapshotMapper snapshotMapper, KnowledgeSnapshotParser parser) {
        this.snapshotMapper = snapshotMapper;
        this.parser = parser;
    }

public Map<String, Object> buildGraph(String snapshotDate) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 统计每个 topic 的语言分布（用于建边）
        Map<String, Map<String, Integer>> topicLanguages = new LinkedHashMap<>();

        for (String topicName : KnowledgeGraphCatalog.TOPIC_NAMES.keySet()) {
            LeaderboardSnapshot snap = "__LATEST__".equals(snapshotDate)
                    ? snapshotMapper.selectLatest("github", topicName, "all")
                    : snapshotMapper.selectByKey("github", topicName, "all", snapshotDate);
            List<Map<String, Object>> repos = snap != null ? parser.parseRepos(snap.getDataJson()) : Collections.emptyList();

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
                for (String kw : KnowledgeGraphCatalog.VC_KEYWORDS) {
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
            node.put("name", KnowledgeGraphCatalog.TOPIC_NAMES.getOrDefault(topicName, topicName));
            node.put("group", KnowledgeGraphCatalog.TOPIC_GROUPS.getOrDefault(topicName, "其他"));
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
            String insight = generateInsight(KnowledgeGraphCatalog.TOPIC_NAMES.getOrDefault(topicName, topicName), repoCount, totalStars, starsPerRepo, langDiversity, crowdedness, difficulty, beginnerRepo, topRepo, 0, 0, vcCount, repoCount > 0 ? vcCount * 100 / repoCount : 0);

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
        List<String> topicList = new ArrayList<>(KnowledgeGraphCatalog.TOPIC_NAMES.keySet());
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

String generateInsight(String name, int repoCount, long totalStars, long starsPerRepo,
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

private String generateTopicSummary(String topicName, List<Map<String, Object>> repos) {
        return KnowledgeGraphCatalog.TOPIC_SUMMARIES.getOrDefault(topicName, "该方向暂无简介");
    }

private String formatK(long n) {
        if (n >= 1000000) return String.format("%.1fM", n / 1000000.0);
        if (n >= 1000) return String.format("%.1fK", n / 1000.0);
        return String.valueOf(n);
    }
}

