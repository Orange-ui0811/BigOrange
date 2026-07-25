package com.orangejd.server.task;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LeaderboardRefreshTask implements SystemTaskHandler {
    public static final String TASK_TYPE = "LEADERBOARD_REFRESH";

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshTask.class);
    private static final String[] TOPICS = {
        "creative-coding","game-development","WebGL","Canvas","Web-Audio",
        "ai-agents","RAG","self-hosted","home-assistant","awesome-self-hosted",
        "ESP32","raspberry-pi","home-automation","3d-printing","other"
    };
    private static final int README_ALL_TOP_N = 20;   // 全时段 Top 20（永久保留）
    private static final int README_WEEK_TOP_N = 3;   // 每周 Top 3（30 天过期）
    private static final int GITHUB_TIMEOUT_MS = 10000;
    private static final int README_TIMEOUT_MS = 10000;

    @Autowired private LeaderboardSnapshotMapper snapshotMapper;
    @Autowired private SystemTaskService systemTaskService;

    @Autowired(required = false)
    private DocumentService documentService;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    @Value("${orange.ai-trend.endpoint:https://ai.gitee.com/v1}")
    private String aiEndpoint;

    @Value("${orange.ai-trend.api-key:}")
    private String aiApiKey;

    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String aiModel;

    private final RestTemplate restTemplate;
    private final RestTemplate readmeRestTemplate;

    // 翻译缓存 — 避免同一描述每天重复调用 AI
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    {
        restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(GITHUB_TIMEOUT_MS);
            setReadTimeout(GITHUB_TIMEOUT_MS);
        }});
        readmeRestTemplate = new RestTemplate();
        readmeRestTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(README_TIMEOUT_MS);
            setReadTimeout(README_TIMEOUT_MS);
        }});
    }

    // 每周一凌晨刷新；cron 和时区可通过生产配置覆盖。
    @Scheduled(
            cron = "${orange.leaderboard.refresh-cron:0 0 2 * * 1}",
            zone = "${orange.leaderboard.refresh-zone:Asia/Shanghai}")
    public void scheduleRefresh() {
        enqueue("scheduled:" + LocalDate.now());
    }

    public void refreshAll() {
        if (!refreshRunning.compareAndSet(false, true)) {
            log.info("LeaderboardRefreshTask: 已有刷新任务运行，跳过重复触发");
            return;
        }
        try {
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

        // 所有爬取完成后，统一翻译描述为中文
        log.info("LeaderboardRefreshTask: 榜单爬取完成，开始翻译描述");
        translateAllToday(today);

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
        } finally {
            refreshRunning.set(false);
        }
    }

    /**
     * 应用启动后检查本周任务是否漏跑。只有最近快照早于本周一时才补抓一次，
     * 同一周内反复重启不会重复刷新。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshMissingSnapshotsAfterStartup() {
        LocalDate weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean missing = isSnapshotBeforeWeek(snapshotMapper.selectLatest("github", null, "all"), weekStart)
                || isSnapshotBeforeWeek(snapshotMapper.selectLatest("github", null, "week"), weekStart)
                || isSnapshotBeforeWeek(snapshotMapper.selectLatest("github", null, "new"), weekStart);
        if (missing) {
                log.info("LeaderboardRefreshTask: 启动后检测到本周快照缺失，开始补抓");
                enqueue("startup:" + weekStart);
        }
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Object execute(SystemTask task) {
        refreshAll();
        return Map.of("refreshedAt", LocalDate.now().toString());
    }

    private boolean isSnapshotBeforeWeek(LeaderboardSnapshot snapshot, LocalDate weekStart) {
        return snapshot == null || snapshot.getSnapshotDate() == null
                || snapshot.getSnapshotDate().isBefore(weekStart);
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

    // ==================== 爬取完成后统一翻译 ====================

    /**
     * 榜单爬取全部完成后，逐条翻译所有缺失 descriptionZh 的描述
     * 翻译失败时持续重试，直到成功。
     */
    private void translateAllToday(LocalDate today) {
        if (aiApiKey == null || aiApiKey.isEmpty()) {
            log.info("AI API Key 未配置，跳过翻译");
            return;
        }
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        int totalDone = 0;
        for (String topic : TOPICS) {
            for (String period : new String[]{"all", "new", "week"}) {
                LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topic, period, todayStr);
                if (snap == null || snap.getDataJson() == null) continue;
                PageResult<Map<String, Object>> parsed = parsePageResult(snap.getDataJson());
                if (parsed == null || parsed.getRecords().isEmpty()) continue;

                boolean updated = false;
                for (Map<String, Object> r : parsed.getRecords()) {
                    if (r.containsKey("descriptionZh") && r.get("descriptionZh") != null
                            && !((String) r.get("descriptionZh")).isEmpty()) continue;
                    String desc = (String) r.getOrDefault("description", "");
                    if (desc == null || desc.trim().isEmpty()) continue;
                    desc = desc.trim();

                    // 缓存命中
                    String cached = translationCache.get(desc);
                    if (cached != null) {
                        if (!cached.isEmpty()) r.put("descriptionZh", cached);
                        updated = true; totalDone++;
                        continue;
                    }

                    // 翻译失败后持续重试，确保最终写入中文描述。
                    String translated = null;
                    int retry = 0;
                    for (int attempt = 0; attempt < 1 && (translated == null || translated.isEmpty()); attempt++) {
                        try {
                            translated = callAiTranslation(desc);
                            if (translated == null || translated.isEmpty()) {
                                retry++;
                                if (retry % 5 == 0) {
                                    log.info("翻译重试中... topic={} period={} retry={}", topic, period, retry);
                                }
                                sleep(2000);
                            }
                        } catch (Exception e) {
                            retry++;
                            if (retry % 5 == 0) {
                                log.info("翻译重试中... topic={} period={} retry={} err={}",
                                        topic, period, retry, e.getMessage());
                            }
                            sleep(2000);
                        }
                    }
                    if (translated == null || translated.isBlank()) {
                        throw new IllegalStateException("AI translation failed; the persistent task will retry later");
                    }
                    translationCache.put(desc, translated);
                    r.put("descriptionZh", translated);
                    updated = true;
                    totalDone++;
                }

                if (updated) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("total", parsed.getTotal());
                    data.put("records", parsed.getRecords());
                    snap.setDataJson(toJson(data));
                    snapshotMapper.insert(snap); // upsert
                }
            }
        }
        log.info("翻译全部完成: 共 {} 条描述已汉化", totalDone);
    }

    private String callAiTranslation(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "将以下英文翻译为简洁中文，只输出翻译结果，不要解释：\n" + text);
        messages.add(msg);
        body.put("messages", messages);
        body.put("max_tokens", 200);
        body.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                aiEndpoint + "/chat/completions", entity, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("choices")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                String content = (String) message.get("content");
                return content != null ? content.trim() : null;
            }
        }
        return null;
    }

    private PageResult<Map<String, Object>> parsePageResult(String json) {
        try {
            int totalStart = json.indexOf("\"total\":") + 8;
            int totalEnd = json.indexOf(",", totalStart);
            long total = Long.parseLong(json.substring(totalStart, totalEnd).trim());
            List<Map<String, Object>> records = new ArrayList<>();
            int arrStart = json.indexOf("\"records\":[") + 11;
            int arrEnd = json.lastIndexOf("]}");
            if (arrEnd < 0) arrEnd = json.length() - 2;
            String arrStr = json.substring(arrStart, arrEnd);
            int depth = 0, objStart = -1;
            for (int i = 0; i < arrStr.length(); i++) {
                if (arrStr.charAt(i) == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (arrStr.charAt(i) == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        String objStr = arrStr.substring(objStart + 1, i);
                        Map<String, Object> map = new LinkedHashMap<>();
                        String[] pairs = objStr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        for (String pair : pairs) {
                            int colon = pair.indexOf(":");
                            if (colon < 0) continue;
                            String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                            String val = pair.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
                            if (val.matches("-?\\d+(\\.\\d+)?")) {
                                if (val.contains(".")) map.put(key, Double.parseDouble(val));
                                else map.put(key, Long.parseLong(val));
                            } else if ("null".equals(val)) {
                                map.put(key, null);
                            } else {
                                map.put(key, val.replace("\\\"", "\"").replace("\\n", "\n"));
                            }
                        }
                        records.add(map);
                    }
                }
            }
            return new PageResult<>(total, records);
        } catch (Exception e) { return null; }
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

    private void enqueue(String businessKey) {
        // maxAttempts=0 preserves retry-until-success semantics without pinning a worker forever.
        systemTaskService.enqueue(TASK_TYPE, businessKey, Map.of(), 0);
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
