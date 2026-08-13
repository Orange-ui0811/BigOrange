package com.orangejd.server.service.leaderboard;
import com.fasterxml.jackson.databind.ObjectMapper; import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.foundation.external.GithubApiClient; import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.slf4j.*; import org.springframework.stereotype.Component; import java.net.URLEncoder; import java.nio.charset.StandardCharsets; import java.time.*; import java.time.format.*; import java.time.temporal.*; import java.util.*;
@Component
class LeaderboardSnapshotRefresher {
 private static final Logger log=LoggerFactory.getLogger(LeaderboardSnapshotRefresher.class);
 private final LeaderboardSnapshotMapper snapshotMapper; private final GithubApiClient githubApiClient; private final ObjectMapper objectMapper; private final LeaderboardReadmeIndexer readmeIndexer; private final LeaderboardProgressReporter progress;
 LeaderboardSnapshotRefresher(LeaderboardSnapshotMapper mapper,GithubApiClient github,ObjectMapper json,LeaderboardReadmeIndexer readmes,LeaderboardProgressReporter progress){this.snapshotMapper=mapper;this.githubApiClient=github;this.objectMapper=json;this.readmeIndexer=readmes;this.progress=progress;}
void refreshAndSave(String topicName, String period, LocalDate today, String isoDate) {
        // 今天已有快照就跳过
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        LeaderboardSnapshot existing = snapshotMapper.selectByKey("github", topicName, period, todayStr);
        if (existing != null && existing.getDataJson() != null) {
            log.debug("GitHub 快照已存在，跳过: topic={}, period={}", topicName, period);
            return;
        }
        try {
            StringBuilder q = new StringBuilder();
            if (topicName != null && !topicName.isEmpty() && !LeaderboardRefreshCoordinator.ALL_TOPICS.equals(topicName)) {
                q.append("topic:").append(topicName).append(" ");
            }
            if ("new".equals(period) && isoDate != null) {
                q.append("created:>").append(isoDate);
            } else {
                q.append("stars:>1");
            }

            String url = searchUrl(q.toString().trim());

            Map<String, Object> response = githubApiClient.getMap(url);

            if (response.containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                int totalCount = ((Number) response.getOrDefault("total_count", 0)).intValue();

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
                progress.itemSucceeded("FETCH", topicName + ":" + period, Map.of("records", records.size()));
                log.info("GitHub 快照已保存: topic={}, period={}", topicName, period);

                // RAG：自动采集 README（all Top 20 / week Top 3）
                if (topicName != null && readmeIndexer.available()) {
                    int topN = "all".equals(period) ? 20 : 3;
                    try { readmeIndexer.fetchAndIndexReadme(topicName, period, records, topN); }
                    catch (Exception e) { log.warn("[RAG] README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            progress.itemFailed("FETCH", topicName + ":" + period, e);
            log.error("GitHub 快照失败 topic={} period={}: {}", topicName, period, e.getMessage());
        }
    }

void refreshWeekStars(String topicName, LocalDate today) {
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
            if (topicName != null && !topicName.isEmpty() && !LeaderboardRefreshCoordinator.ALL_TOPICS.equals(topicName)) {
                q.append("topic:").append(topicName).append(" ");
            }
            // pushed: 近期有代码推送 → 活跃仓库 → 更可能在涨星
            q.append("pushed:>").append(pushedAfter).append(" stars:>10");

            String url = searchUrl(q.toString().trim());

            Map<String, Object> response = githubApiClient.getMap(url);

            if (response.containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                int totalCount = ((Number) response.getOrDefault("total_count", 0)).intValue();

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
                progress.itemSucceeded("FETCH", topicName + ":week", Map.of("records", records.size()));
                log.info("GitHub 涨星快照已保存: topic={}, period=week, pushed>{}", topicName, pushedAfter);

                // RAG：本周涨星 Top 5 自动采集 README
                if (topicName != null && readmeIndexer.available()) {
                    try { readmeIndexer.fetchAndIndexReadme(topicName, "week", records, 5); }
                    catch (Exception e) { log.warn("[RAG] 涨星 README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            progress.itemFailed("FETCH", topicName + ":week", e);
            log.error("GitHub 涨星快照失败 topic={}: {}", topicName, e.getMessage());
        }
    }

private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("榜单快照序列化失败", ex);
}

}

static String searchUrl(String query) {
    return "https://api.github.com/search/repositories?q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&sort=stars&order=desc&per_page=20";
}
}
