package com.orangejd.server.task;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.external.TranslationCacheMapper;
import com.orangejd.server.foundation.external.GithubApiClient;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.service.DocumentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    private static final String ALL_TOPICS = "__ALL__";
    private static final int TRANSLATION_BATCH_SIZE = 8;

    @Autowired private LeaderboardSnapshotMapper snapshotMapper;
    @Autowired private SystemTaskService systemTaskService;
    @Autowired private IngestionCoordinatorService ingestionCoordinator;
    @Autowired private IngestionRunService ingestionRunService;
    @Autowired private AiRuntimeConfigService aiConfigService;
    @Autowired private ExternalAiClient externalAiClient;
    @Autowired private TranslationCacheMapper translationCacheMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GithubApiClient githubApiClient;

    @Autowired(required = false)
    private DocumentService documentService;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    @Value("${orange.ingestion.pipeline-v2-enabled:false}")
    private boolean pipelineV2Enabled;

    private final RestTemplate readmeRestTemplate;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private final ThreadLocal<RunContext> runContext = new ThreadLocal<>();

    {
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
        if (pipelineV2Enabled) {
            ingestionCoordinator.scheduled(IngestionCoordinatorService.LEADERBOARD_FULL,
                    LocalDate.now().toString());
        } else {
            enqueue("scheduled:" + LocalDate.now());
        }
    }

    public void refreshAll() {
        refreshAll(null, null);
    }

    private void refreshAll(Long runId, String configVersion) {
        if (!refreshRunning.compareAndSet(false, true)) {
            log.info("LeaderboardRefreshTask: 已有刷新任务运行，跳过重复触发");
            return;
        }
        RunContext context = new RunContext(runId, configVersion);
        runContext.set(context);
        try {
        log.info("LeaderboardRefreshTask: 开始刷新 GitHub 榜单");
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 周一当天用上周一，避免 GitHb 查询只覆盖一天
        if (monday.equals(today)) monday = monday.minusWeeks(1);
        String weekStart = monday.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        int totalStages = (TOPICS.length + 1) * 3;
        context.total = totalStages;
        for (String topic : TOPICS) {
            checkCancelled();
            refreshAndSave(topic, "all", today, null);
            sleep(6000);
            String isoDate = monday
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            refreshAndSave(topic, "new", today, isoDate);   // 本周新增
            sleep(6000);
            refreshWeekStars(topic, today);                  // 本周涨星
            sleep(6000);
        }

        // 聚合榜单使用稳定键，读取层仍兼容旧 NULL 快照。
        refreshAndSave(ALL_TOPICS, "all", today, null);
        sleep(6000);
        refreshAndSave(ALL_TOPICS, "new", today, monday.format(DateTimeFormatter.ISO_LOCAL_DATE));
        sleep(6000);
        refreshWeekStars(ALL_TOPICS, today);

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
            runContext.remove();
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
        boolean missing = isSnapshotBeforeWeek(selectAggregateLatest("all"), weekStart)
                || isSnapshotBeforeWeek(selectAggregateLatest("week"), weekStart)
                || isSnapshotBeforeWeek(selectAggregateLatest("new"), weekStart);
        if (missing) {
                log.info("LeaderboardRefreshTask: 启动后检测到本周快照缺失，开始补抓");
                if (pipelineV2Enabled) {
                    ingestionCoordinator.start(IngestionCoordinatorService.LEADERBOARD_FULL,
                            "STARTUP", "startup:" + weekStart, Map.of());
                } else {
                    enqueue("startup:" + weekStart);
                }
        }
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Object execute(SystemTask task) {
        refreshAll(task.getRunId(), task.getConfigVersion());
        RunContext context = runContext.get();
        return Map.of("refreshedAt", LocalDate.now().toString(),
                "runId", task.getRunId() == null ? 0 : task.getRunId());
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
            if (topicName != null && !topicName.isEmpty() && !ALL_TOPICS.equals(topicName)) {
                q.append("topic:").append(topicName).append(" ");
            }
            if ("new".equals(period) && isoDate != null) {
                q.append("created:>").append(isoDate);
            } else {
                q.append("stars:>1");
            }

            String url = "https://api.github.com/search/repositories?q=" + q.toString().trim()
                    + "&sort=stars&order=desc&per_page=20";

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
                itemSucceeded("FETCH", topicName + ":" + period, Map.of("records", records.size()));
                log.info("GitHub 快照已保存: topic={}, period={}", topicName, period);

                // RAG：自动采集 README（all Top 20 / week Top 3）
                if (topicName != null && documentService != null) {
                    int topN = "all".equals(period) ? README_ALL_TOP_N : README_WEEK_TOP_N;
                    try { fetchAndIndexReadme(topicName, period, records, topN); }
                    catch (Exception e) { log.warn("[RAG] README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            itemFailed("FETCH", topicName + ":" + period, e);
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
            if (topicName != null && !topicName.isEmpty() && !ALL_TOPICS.equals(topicName)) {
                q.append("topic:").append(topicName).append(" ");
            }
            // pushed: 近期有代码推送 → 活跃仓库 → 更可能在涨星
            q.append("pushed:>").append(pushedAfter).append(" stars:>10");

            String url = "https://api.github.com/search/repositories?q=" + q.toString().trim()
                    + "&sort=stars&order=desc&per_page=20";

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
                itemSucceeded("FETCH", topicName + ":week", Map.of("records", records.size()));
                log.info("GitHub 涨星快照已保存: topic={}, period=week, pushed>{}", topicName, pushedAfter);

                // RAG：本周涨星 Top 5 自动采集 README
                if (topicName != null && documentService != null) {
                    try { fetchAndIndexReadme(topicName, "week", records, 5); }
                    catch (Exception e) { log.warn("[RAG] 涨星 README 采集异常 topic={}: {}", topicName, e.getMessage()); }
                }
            }
        } catch (Exception e) {
            itemFailed("FETCH", topicName + ":week", e);
            log.error("GitHub 涨星快照失败 topic={}: {}", topicName, e.getMessage());
        }
    }

    // ==================== 爬取完成后统一翻译 ====================

    /**
     * 榜单爬取全部完成后，逐条翻译所有缺失 descriptionZh 的描述
     * 翻译失败时持续重试，直到成功。
     */
    private void translateAllToday(LocalDate today) {
        RunContext context = currentContext();
        AiRuntimeConfig config = aiConfigService.resolveText(context.configVersion);
        if (config.apiKey() == null || config.apiKey().isEmpty()) {
            log.info("AI API Key 未配置，跳过翻译");
            return;
        }
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        int totalDone = 0;
        List<String> topics = new ArrayList<>(Arrays.asList(TOPICS));
        topics.add(ALL_TOPICS);
        for (String topic : topics) {
            for (String period : new String[]{"all", "new", "week"}) {
                LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topic, period, todayStr);
                if (snap == null || snap.getDataJson() == null) continue;
                PageResult<Map<String, Object>> parsed = parsePageResult(snap.getDataJson());
                if (parsed == null || parsed.getRecords().isEmpty()) continue;

                boolean updated = false;
                List<Map<String, Object>> missing = new ArrayList<>();
                for (Map<String, Object> r : parsed.getRecords()) {
                    if (r.containsKey("descriptionZh") && r.get("descriptionZh") != null
                            && !((String) r.get("descriptionZh")).isEmpty()) continue;
                    String desc = (String) r.getOrDefault("description", "");
                    if (desc == null || desc.trim().isEmpty()) continue;
                    String cached = cachedTranslation(desc.trim(), config.model());
                    if (cached != null) {
                        r.put("descriptionZh", cached);
                        updated = true;
                        totalDone++;
                    } else {
                        missing.add(r);
                    }
                }
                for (int start = 0; start < missing.size(); start += TRANSLATION_BATCH_SIZE) {
                    List<Map<String, Object>> batch = missing.subList(start,
                            Math.min(start + TRANSLATION_BATCH_SIZE, missing.size()));
                    try {
                        int done = translateBatch(batch, config);
                        totalDone += done;
                        updated |= done > 0;
                    } catch (Exception ex) {
                        itemFailed("TRANSLATE", topic + ":" + period + ":" + start, ex);
                        log.warn("AI translation batch failed topic={} period={} start={} message={}",
                                topic, period, start, abbreviate(ex.getMessage()));
                    }
                    persistTranslatedSnapshot(snap, parsed, updated);
                }

                persistTranslatedSnapshot(snap, parsed, updated);
            }
        }
        log.info("翻译全部完成: 共 {} 条描述已汉化", totalDone);
    }

    private void persistTranslatedSnapshot(LeaderboardSnapshot snap,
                                           PageResult<Map<String, Object>> parsed,
                                           boolean updated) {
        if (!updated) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", parsed.getTotal());
        data.put("records", parsed.getRecords());
        snap.setDataJson(toJson(data));
        snapshotMapper.insert(snap);
    }

    private int translateBatch(List<Map<String, Object>> batch, AiRuntimeConfig config) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            input.add(Map.of("index", i, "text", String.valueOf(batch.get(i).get("description"))));
        }
        String prompt;
        try {
            prompt = objectMapper.writeValueAsString(input);
        } catch (Exception ex) {
            throw new IllegalArgumentException("榜单翻译输入序列化失败", ex);
        }
        String content = externalAiClient.call(config,
                "你是开源项目翻译助手。忠实、简洁地翻译，不添加原文没有的信息。",
                "将每项 text 翻译为简体中文。严格输出 JSON："
                        + "{\"items\":[{\"index\":0,\"translated\":\"\"}]}。\n" + prompt,
                0.1, "leaderboard-translation");
        try {
            JsonNode items = objectMapper.readTree(content).path("items");
            Map<Integer, String> translated = new HashMap<>();
            if (items.isArray()) {
                items.forEach(node -> translated.put(node.path("index").asInt(-1),
                        node.path("translated").asText("")));
            }
            int success = 0;
            for (int i = 0; i < batch.size(); i++) {
                String value = translated.get(i);
                if (value == null || value.isBlank()) continue;
                Map<String, Object> record = batch.get(i);
                String source = String.valueOf(record.get("description")).trim();
                record.put("descriptionZh", value.trim());
                saveTranslation(source, config.model(), value.trim());
                itemSucceeded("TRANSLATE", String.valueOf(record.get("fullName")), null);
                success++;
            }
            if (success != batch.size()) {
                throw new IllegalStateException("榜单翻译批次返回不完整: " + success + "/" + batch.size());
            }
            return success;
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("榜单翻译响应格式无效", ex);
        }
    }

    static String resolveChatCompletionsUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("AI endpoint is not configured");
        }
        String normalized = endpoint.trim().replaceAll("/+$", "");
        return normalized.endsWith("/chat/completions")
                ? normalized
                : normalized + "/chat/completions";
    }

    private String cachedTranslation(String source, String model) {
        String hash = sha256(source);
        String value = translationCacheMapper.find(hash, "LEADERBOARD", model);
        if (value != null) translationCacheMapper.touch(hash, "LEADERBOARD", model);
        return value;
    }

    private void saveTranslation(String source, String model, String translated) {
        translationCacheMapper.save(sha256(source), "LEADERBOARD", model, translated);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算翻译缓存键", ex);
        }
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) return "no details";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private PageResult<Map<String, Object>> parsePageResult(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
            long total = data.get("total") instanceof Number number ? number.longValue() : 0L;
            Object recordsValue = data.get("records");
            List<Map<String, Object>> records = recordsValue == null
                    ? Collections.emptyList()
                    : objectMapper.convertValue(recordsValue, new TypeReference<>() {});
            return new PageResult<>(total, records);
        } catch (Exception e) {
            log.warn("榜单快照 JSON 无法解析: {}", e.getMessage());
            return new PageResult<>(0L, Collections.emptyList());
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
                documentService.importFromGithub(fullName, content, period, currentContext().configVersion);
                imported++;
                itemSucceeded("README", sourceRef, null);
            } catch (Exception e) {
                itemFailed("README", sourceRef, e);
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
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("榜单刷新被中断", ex);
        }
    }

    private void enqueue(String businessKey) {
        // maxAttempts=0 preserves retry-until-success semantics without pinning a worker forever.
        systemTaskService.enqueue(TASK_TYPE, businessKey, Map.of(), 0);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("榜单快照序列化失败", ex);
        }
    }

    private void checkCancelled() {
        RunContext context = currentContext();
        if (context.runId != null && ingestionRunService.cancelled(context.runId)) {
            throw new IllegalStateException("抓取任务已取消");
        }
    }

    private void itemSucceeded(String stage, String key, Object result) {
        RunContext context = currentContext();
        context.success++;
        ingestionRunService.item(context.runId, stage, key, "SUCCEEDED", result, null);
        publishProgress(context);
    }

    private void itemFailed(String stage, String key, Exception error) {
        RunContext context = currentContext();
        context.failed++;
        ingestionRunService.item(context.runId, stage, key, "FAILED", null, abbreviate(error.getMessage()));
        publishProgress(context);
    }

    private void publishProgress(RunContext context) {
        ingestionRunService.progress(context.runId, Math.max(context.total, context.success + context.failed),
                context.success, context.failed, context.skipped,
                context.failed > 0 ? "存在失败项，可在管理端查看并重试" : null);
    }

    private RunContext currentContext() {
        RunContext context = runContext.get();
        return context == null ? new RunContext(null, null) : context;
    }

    private LeaderboardSnapshot selectAggregateLatest(String period) {
        LeaderboardSnapshot snapshot = snapshotMapper.selectLatest("github", ALL_TOPICS, period);
        return snapshot != null ? snapshot : snapshotMapper.selectLatest("github", null, period);
    }

    private static final class RunContext {
        private final Long runId;
        private final String configVersion;
        private int total;
        private int success;
        private int failed;
        private int skipped;

        private RunContext(Long runId, String configVersion) {
            this.runId = runId;
            this.configVersion = configVersion;
        }
    }
}
