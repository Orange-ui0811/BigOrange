package com.orangejd.server.task;
import com.fasterxml.jackson.core.type.TypeReference; import com.fasterxml.jackson.databind.*; import com.orangejd.common.result.PageResult; import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.foundation.external.*; import com.orangejd.server.mapper.LeaderboardSnapshotMapper; import org.slf4j.*; import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.*; import java.time.format.*; import java.util.*;
@Component
class LeaderboardTranslationPipeline {
 private static final Logger log=LoggerFactory.getLogger(LeaderboardTranslationPipeline.class);
 private final LeaderboardSnapshotMapper snapshotMapper; private final AiRuntimeConfigService aiConfigService; private final ExternalAiClient externalAiClient; private final TranslationCacheMapper translationCacheMapper; private final ObjectMapper objectMapper; private final LeaderboardProgressReporter progress;
 LeaderboardTranslationPipeline(LeaderboardSnapshotMapper mapper,AiRuntimeConfigService configs,ExternalAiClient ai,TranslationCacheMapper cache,ObjectMapper json,LeaderboardProgressReporter progress){this.snapshotMapper=mapper;this.aiConfigService=configs;this.externalAiClient=ai;this.translationCacheMapper=cache;this.objectMapper=json;this.progress=progress;}
void translateAllToday(LocalDate today) {
        LeaderboardRunContext context = progress.current();
        AiRuntimeConfig config = aiConfigService.resolveText(context.configVersion);
        if (config.apiKey() == null || config.apiKey().isEmpty()) {
            log.info("AI API Key 未配置，跳过翻译");
            return;
        }
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        int totalDone = 0;
        List<String> topics = new ArrayList<>(Arrays.asList(LeaderboardRefreshCoordinator.TOPICS));
        topics.add(LeaderboardRefreshCoordinator.ALL_TOPICS);
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
                for (int start = 0; start < missing.size(); start += 8) {
                    List<Map<String, Object>> batch = missing.subList(start,
                            Math.min(start + 8, missing.size()));
                    try {
                        int done = translateBatch(batch, config);
                        totalDone += done;
                        updated |= done > 0;
                    } catch (Exception ex) {
                        progress.itemFailed("TRANSLATE", topic + ":" + period + ":" + start, ex);
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
                progress.itemSucceeded("TRANSLATE", String.valueOf(record.get("fullName")), null);
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

private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("榜单快照序列化失败", ex);
        }
    }
}
