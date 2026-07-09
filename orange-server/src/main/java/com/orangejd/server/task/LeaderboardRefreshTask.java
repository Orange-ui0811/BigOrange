package com.orangejd.server.task;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import com.orangejd.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Component
public class LeaderboardRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshTask.class);
    private static final String[] TOPICS = {
        "creative-coding","game-development","WebGL","Canvas","Web-Audio",
        "ai-agents","RAG","self-hosted","home-assistant","awesome-self-hosted",
        "ESP32","raspberry-pi","home-automation","3d-printing","other"
    };
    private static final int README_ALL_TOP_N = 20;   // 全时段 Top 20（永久保留）
    private static final int README_WEEK_TOP_N = 3;   // 每周 Top 3（30 天过期）
    private static final int README_TIMEOUT_MS = 10000;

    @Autowired private LeaderboardSnapshotMapper snapshotMapper;

    @Autowired(required = false)
    private DocumentService documentService;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate readmeRestTemplate;

    {
        readmeRestTemplate = new RestTemplate();
        readmeRestTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(README_TIMEOUT_MS);
            setReadTimeout(README_TIMEOUT_MS);
        }});
    }

    // 应用启动后首次预热（异步，不阻塞启动）
    @PostConstruct
    public void init() {
        new Thread(() -> {
            try { Thread.sleep(30000); } catch (Exception ignored) {}
            log.info("LeaderboardRefreshTask: 启动预热刷新");
            refreshAll();
        }).start();
    }

    // 每周一凌晨 2:00 执行
    @Scheduled(cron = "0 0 2 * * 1")
    public void refreshAll() {
        log.info("LeaderboardRefreshTask: 开始刷新 GitHub 榜单");
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 周一当天用上周一，避免 GitHb 查询只覆盖一天
        if (monday.equals(today)) monday = monday.minusWeeks(1);
        String weekStart = monday.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        for (String topic : TOPICS) {
            refreshAndSave(topic, "all", today, null);
            sleep(6000);
            String isoDate = monday
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            refreshAndSave(topic, "new", today, isoDate);   // 本周新增
            sleep(6000);
            refreshWeekStars(topic, today);                  // 本周涨星
            sleep(6000);
        }

        // 也刷新全部分类的（topic_name=NULL）
        refreshAndSave(null, "all", today, null);
        sleep(6000);
        refreshAndSave(null, "new", today, monday.format(DateTimeFormatter.ISO_LOCAL_DATE));
        sleep(6000);
        refreshWeekStars(null, today);

        // 清理 30 天前的旧数据
        String beforeDate = today.minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        int deleted = snapshotMapper.deleteBefore(beforeDate);
        log.info("LeaderboardRefreshTask: 完成 GitHub 榜单刷新，清理 {} 条旧快照", deleted);

        // 清理 30 天前的每周 README
        if (documentService instanceof com.orangejd.server.service.impl.DocumentServiceImpl impl) {
            try {
                int cleaned = impl.cleanExpiredWeekly(beforeDate);
                if (cleaned > 0) log.info("[RAG] 清理过期 weekly README {} 篇", cleaned);
            } catch (Exception e) {
                log.warn("[RAG] weekly 清理异常: {}", e.getMessage());
            }
        }
    }

    private void refreshAndSave(String topicName, String period, LocalDate today, String isoDate) {
        // 今天已有快照就跳过
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        LeaderboardSnapshot existing = snapshotMapper.selectByKey("github", topicName, period, todayStr);
        if (existing != null && existing.getDataJson() != null) {
            log.debug("GitHub 快照已存在，跳过: topic={}, period={}", topicName, period);
            return;
        }
        try {
            StringBuilder q = new StringBuilder();
            if (topicName != null && !topicName.isEmpty()) {
                q.append("topic:").append(topicName).append(" ");
            }
            if ("new".equals(period) && isoDate != null) {
                q.append("created:>").append(isoDate);
            } else {
                q.append("stars:>1");
            }

            String url = "https://api.github.com/search/repositories?q=" + q.toString().trim()
                    + "&sort=stars&order=desc&per_page=20";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            if (githubToken != null && !githubToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + githubToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                int totalCount = (int) response.getBody().getOrDefault("total_count", 0);

                List<Map<String, Object>> records = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", item.get("id"));
                    r.put("name", item.get("name"));
                    r.put("fullName", item.get("full_name"));
                    r.put("description", item.getOrDefault("description", ""));
                    r.put("htmlUrl", item.get("html_url"));
                    r.put("language", item.getOrDefault("language", ""));
                    r.put("stars", item.get("stargazers_count"));
                    r.put("forks", item.get("forks_count"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> owner = (Map<String, Object>) item.get("owner");
                    r.put("ownerName", owner != null ? owner.get("login") : "");
                    r.put("ownerAvatar", owner != null ? owner.get("avatar_url") : "");
                    records.add(r);
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("total", totalCount);
                data.put("records", records);

                String json = toJson(data);
                LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
                snapshot.setSource("github");
                snapshot.setTopicName(topicName);
                snapshot.setPeriod(period);
                snapshot.setSnapshotDate(today);
                snapshot.setDataJson(json);
                snapshotMapper.insert(snapshot);
                log.info("GitHub 快照已保存: topic={}, period={}", topicName, period);

                // RAG：自动采集 README（all Top 20 / week Top 3）
                if (topicName != null && documentService != null) {
                    int topN = "all".equals(period) ? README_ALL_TOP_N : README_WEEK_TOP_N;
                    try { fetchAndIndexReadme(topicName, period, records, topN); }
                    catch (Exception e) { log.warn("[RAG] README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            log.error("GitHub 快照失败 topic={} period={}: {}", topicName, period, e.getMessage());
        }
    }

    // ==================== 本周涨星（近期活跃 + 高星 = 趋势上涨） ====================

    /**
     * 爬取本周涨星最快的项目
     * 策略：pushed:>上周一 — 近期有代码推送说明仓库活跃，结合高星数近似"正在涨星"
     * 条件 stars:>10 过滤低质量仓库，sort=stars 按星数降序
     * 规则：先爬取完成"新增"再爬取"涨星"，避免 GitHub API 限流
     */
    private void refreshWeekStars(String topicName, LocalDate today) {
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        LeaderboardSnapshot existing = snapshotMapper.selectByKey("github", topicName, "week", todayStr);
        if (existing != null && existing.getDataJson() != null) {
            log.debug("GitHub 涨星快照已存在，跳过: topic={}", topicName);
            return;
        }
        try {
            // 计算上周一日期
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (monday.equals(today)) monday = monday.minusWeeks(1);
            String pushedAfter = monday.format(DateTimeFormatter.ISO_LOCAL_DATE);

            StringBuilder q = new StringBuilder();
            if (topicName != null && !topicName.isEmpty()) {
                q.append("topic:").append(topicName).append(" ");
            }
            // pushed: 近期有代码推送 → 活跃仓库 → 更可能在涨星
            q.append("pushed:>").append(pushedAfter).append(" stars:>10");

            String url = "https://api.github.com/search/repositories?q=" + q.toString().trim()
                    + "&sort=stars&order=desc&per_page=20";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            if (githubToken != null && !githubToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + githubToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                int totalCount = (int) response.getBody().getOrDefault("total_count", 0);

                List<Map<String, Object>> records = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", item.get("id"));
                    r.put("name", item.get("name"));
                    r.put("fullName", item.get("full_name"));
                    r.put("description", item.getOrDefault("description", ""));
                    r.put("htmlUrl", item.get("html_url"));
                    r.put("language", item.getOrDefault("language", ""));
                    r.put("stars", item.get("stargazers_count"));
                    r.put("forks", item.get("forks_count"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> owner = (Map<String, Object>) item.get("owner");
                    r.put("ownerName", owner != null ? owner.get("login") : "");
                    r.put("ownerAvatar", owner != null ? owner.get("avatar_url") : "");
                    records.add(r);
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("total", totalCount);
                data.put("records", records);

                String json = toJson(data);
                LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
                snapshot.setSource("github");
                snapshot.setTopicName(topicName);
                snapshot.setPeriod("week");
                snapshot.setSnapshotDate(today);
                snapshot.setDataJson(json);
                snapshotMapper.insert(snapshot);
                log.info("GitHub 涨星快照已保存: topic={}, period=week, pushed>{}", topicName, pushedAfter);

                // RAG：本周涨星 Top 5 自动采集 README
                if (topicName != null && documentService != null) {
                    try { fetchAndIndexReadme(topicName, "week", records, 5); }
                    catch (Exception e) { log.warn("[RAG] 涨星 README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            log.error("GitHub 涨星快照失败 topic={}: {}", topicName, e.getMessage());
        }
    }

    // ==================== RAG README 自动采集 ====================

    private void fetchAndIndexReadme(String topicName, String period,
                                      List<Map<String, Object>> records, int topN) {
        int imported = 0;
        for (int i = 0; i < Math.min(topN, records.size()); i++) {
            String fullName = (String) records.get(i).get("fullName");
            if (fullName == null) continue;

            String sourceRef = ("new".equals(period) || "week".equals(period))
                    ? fullName + "|" + period : fullName;
            if (documentService.existsBySourceRef(sourceRef)) continue;

            String content = fetchReadme(fullName);
            if (content == null) continue;

            try {
                documentService.importFromGithub(fullName, content, period);
                imported++;
            } catch (Exception e) {
                log.warn("[RAG] README 入库失败 {}: {}", fullName, e.getMessage());
            }
        }
        if (imported > 0) {
            log.info("[RAG] topic={} period={} 自动采集 README {} 篇", topicName, period, imported);
        }
    }

    private String fetchReadme(String fullName) {
        // 先试 master 分支
        String content = fetchUrl("https://raw.githubusercontent.com/" + fullName + "/master/README.md");
        if (content != null) return content;
        // 再试 main 分支
        return fetchUrl("https://raw.githubusercontent.com/" + fullName + "/main/README.md");
    }

    private String fetchUrl(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (githubToken != null && !githubToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + githubToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = readmeRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                return resp.getBody();
            }
        } catch (Exception ignored) {
            // README 拉取失败静默跳过
        }
        return null;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    // 简单 JSON 序列化
    private String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"total\":").append(data.get("total")).append(",\"records\":[");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        boolean first = true;
        for (Map<String, Object> m : records) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            boolean ff = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!ff) sb.append(",");
                ff = false;
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v);
                else sb.append("\"").append(v.toString().replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
