package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/ai-config")
public class AdminAIConfigController {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // 从 YAML 读取默认值（兜底）
    @Value("${orange.ai-trend.endpoint:}")
    private String defaultTrendEndpoint;
    @Value("${orange.ai-trend.api-key:}")
    private String defaultTrendKey;
    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String defaultTrendModel;

    @Value("${orange.ai-audit.endpoint:}")
    private String defaultAuditEndpoint;
    @Value("${orange.ai-audit.api-key:}")
    private String defaultAuditKey;
    @Value("${orange.ai-audit.model:}")
    private String defaultAuditModel;

    @Value("${orange.ai-embedding.endpoint:}")
    private String defaultEmbeddingEndpoint;
    @Value("${orange.ai-embedding.api-key:}")
    private String defaultEmbeddingKey;
    @Value("${orange.ai-embedding.model:bge-small-zh-v1.5}")
    private String defaultEmbeddingModel;

    @GetMapping
    public Result<Map<String, Object>> getAll() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("trend", getConfig("ai:trend", defaultTrendEndpoint, defaultTrendKey, defaultTrendModel));
        config.put("audit", getConfig("ai:audit", defaultAuditEndpoint, defaultAuditKey, defaultAuditModel));
        config.put("embedding", getConfig("ai:embedding", defaultEmbeddingEndpoint, defaultEmbeddingKey, defaultEmbeddingModel));
        config.put("leaderboard", Map.of("type", "GitHub Search API", "url", "https://api.github.com/search/repositories", "desc", "无需 API Key，通过定时任务拉取 15 个 topic 的仓库数据"));
        return Result.success(config);
    }

    @PostMapping("/save")
    public Result<?> save(@RequestBody Map<String, String> body) {
        if (redisTemplate == null) return Result.error("Redis 不可用，无法保存");

        String prefix = body.get("prefix"); // ai:trend / ai:audit / ai:embedding
        if (prefix == null) return Result.error("缺少 prefix 参数");

        redisTemplate.opsForValue().set(prefix + ":endpoint", body.getOrDefault("endpoint", ""));
        redisTemplate.opsForValue().set(prefix + ":api-key", body.getOrDefault("apiKey", ""));
        redisTemplate.opsForValue().set(prefix + ":model", body.getOrDefault("model", ""));
        return Result.success();
    }

    private Map<String, String> getConfig(String prefix, String defaultEndpoint, String defaultKey, String defaultModel) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("endpoint", getVal(prefix + ":endpoint", defaultEndpoint));
        m.put("apiKey", maskKey(getVal(prefix + ":api-key", defaultKey)));
        m.put("model", getVal(prefix + ":model", defaultModel));
        return m;
    }

    private String getVal(String key, String fallback) {
        if (redisTemplate == null) return fallback;
        String v = redisTemplate.opsForValue().get(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 12) return "***";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
