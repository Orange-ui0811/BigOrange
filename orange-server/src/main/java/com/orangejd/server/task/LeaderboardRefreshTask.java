package com.orangejd.server.task;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
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

    @Autowired private LeaderboardSnapshotMapper snapshotMapper;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    private final RestTemplate restTemplate = new RestTemplate();

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
        String weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        for (String topic : TOPICS) {
            refreshAndSave(topic, "all", today, null);
            sleep(6000);
            String isoDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            refreshAndSave(topic, "week", today, isoDate);
            sleep(6000);
        }

        // 也刷新全部分类的（topic_name=NULL）
        refreshAndSave(null, "all", today, null);
        sleep(6000);
        refreshAndSave(null, "week", today, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .format(DateTimeFormatter.ISO_LOCAL_DATE));

        // 清理 30 天前的旧数据
        String beforeDate = today.minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        int deleted = snapshotMapper.deleteBefore(beforeDate);
        log.info("LeaderboardRefreshTask: 完成 GitHub 榜单刷新，清理 {} 条旧快照", deleted);
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
            if ("week".equals(period) && isoDate != null) {
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
            }
        } catch (Exception e) {
            log.error("GitHub 快照失败 topic={} period={}: {}", topicName, period, e.getMessage());
        }
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
