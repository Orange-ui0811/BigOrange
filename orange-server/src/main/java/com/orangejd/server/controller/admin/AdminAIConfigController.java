package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/ai-config")
public class AdminAIConfigController {
    private final AiRuntimeConfigService configService;
    private final ExternalAiClient aiClient;

    public AdminAIConfigController(AiRuntimeConfigService configService, ExternalAiClient aiClient) {
        this.configService = configService;
        this.aiClient = aiClient;
    }

    @GetMapping
    public Result<Map<String, Object>> getAll() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("project", configService.describe(AiRuntimeConfigService.PROJECT));
        config.put("contribution", configService.describe(AiRuntimeConfigService.CONTRIBUTION));
        config.put("trend", configService.describe(AiRuntimeConfigService.TREND));
        config.put("audit", configService.describe(AiRuntimeConfigService.AUDIT));
        config.put("embedding", configService.describe(AiRuntimeConfigService.EMBEDDING));
        config.put("leaderboard", Map.of(
                "type", "GitHub Search API",
                "url", "https://api.github.com/search/repositories",
                "desc", "榜单、Issue 与 README 任务由统一抓取中心记录进度和失败项"));
        return Result.success(config);
    }

    @PostMapping("/save")
    public Result<?> save(@RequestBody Map<String, String> body) {
        String prefix = body.getOrDefault("prefix", "");
        String type = switch (prefix) {
            case "ai:project" -> AiRuntimeConfigService.PROJECT;
            case "ai:contribution" -> AiRuntimeConfigService.CONTRIBUTION;
            case "ai:trend" -> AiRuntimeConfigService.TREND;
            case "ai:audit" -> AiRuntimeConfigService.AUDIT;
            case "ai:embedding" -> AiRuntimeConfigService.EMBEDDING;
            default -> throw new IllegalArgumentException("AI 配置类型无效");
        };
        AiRuntimeConfig saved = configService.save(type,
                body.get("endpoint"), body.get("apiKey"), body.get("model"));
        return Result.success(Map.of("version", saved.version(), "source", saved.source()));
    }

    @PostMapping("/test")
    public Result<?> test(@RequestBody Map<String, String> body) {
        String type = body.getOrDefault("type", AiRuntimeConfigService.TREND);
        AiRuntimeConfig config = configService.current(type);
        if (AiRuntimeConfigService.EMBEDDING.equals(type)) {
            return Result.success(Map.of(
                    "configured", !config.apiKey().isBlank(),
                    "source", "ENV",
                    "message", "Embedding 在应用启动时固定；修改环境变量后需重启并核对向量维度"));
        }
        String content = aiClient.call(config, "你是连接测试助手。", "只回复：连接成功", 0, "admin-config-test");
        return Result.success(Map.of("message", content, "version", config.version()));
    }
}
