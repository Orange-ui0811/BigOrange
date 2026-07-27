package com.orangejd.server.foundation.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.orangejd.common.util.UrlValidator;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class AiRuntimeConfigService {
    public static final String TREND = "trend";
    public static final String PROJECT = "project";
    public static final String CONTRIBUTION = "contribution";
    public static final String AUDIT = "audit";
    public static final String EMBEDDING = "embedding";
    private static final Set<String> DYNAMIC_TYPES = Set.of(
            TREND, PROJECT, CONTRIBUTION, AUDIT);
    private static final String ROOT = "orangevc:ai-config:";

    private final StringRedisTemplate redis;
    private final AiConfigCrypto crypto;
    private final Map<String, AiRuntimeConfig> environment;
    private final boolean production;

    public AiRuntimeConfigService(
            StringRedisTemplate redis,
            AiConfigCrypto crypto,
            @Value("${orange.ai-trend.endpoint:}") String trendEndpoint,
            @Value("${orange.ai-trend.api-key:}") String trendKey,
            @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}") String trendModel,
            @Value("${orange.ai-project.endpoint:}") String projectEndpoint,
            @Value("${orange.ai-project.api-key:}") String projectKey,
            @Value("${orange.ai-project.model:}") String projectModel,
            @Value("${orange.ai-contribution.endpoint:}") String contributionEndpoint,
            @Value("${orange.ai-contribution.api-key:}") String contributionKey,
            @Value("${orange.ai-contribution.model:}") String contributionModel,
            @Value("${orange.ai-audit.endpoint:}") String auditEndpoint,
            @Value("${orange.ai-audit.api-key:}") String auditKey,
            @Value("${orange.ai-audit.model:}") String auditModel,
            @Value("${orange.ai-embedding.endpoint:}") String embeddingEndpoint,
            @Value("${orange.ai-embedding.api-key:}") String embeddingKey,
            @Value("${orange.ai-embedding.model:bge-small-zh-v1.5}") String embeddingModel,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.redis = redis;
        this.crypto = crypto;
        this.production = activeProfiles.contains("prod");
        this.environment = Map.of(
                TREND, env(TREND, trendEndpoint, trendKey, trendModel),
                PROJECT, envWithFallback(PROJECT, projectEndpoint, projectKey, projectModel,
                        trendEndpoint, trendKey, trendModel),
                CONTRIBUTION, envWithFallback(CONTRIBUTION,
                        contributionEndpoint, contributionKey, contributionModel,
                        trendEndpoint, trendKey, trendModel),
                AUDIT, env(AUDIT, auditEndpoint, auditKey, auditModel),
                EMBEDDING, env(EMBEDDING, embeddingEndpoint, embeddingKey, embeddingModel));
    }

    public AiRuntimeConfig current(String type) {
        validateType(type);
        if (!DYNAMIC_TYPES.contains(type)) return environment.get(type);
        try {
            String version = redis.opsForValue().get(ROOT + type + ":active");
            if (StringUtils.hasText(version)) return resolve(type, version);
        } catch (RuntimeException ignored) {
            // Environment variables are the disaster fallback when Redis is unavailable.
        }
        if ((PROJECT.equals(type) || CONTRIBUTION.equals(type))
                && !hasDedicatedEnvironment(type)) {
            AiRuntimeConfig fallback = current(TREND);
            return new AiRuntimeConfig(type, fallback.version(), fallback.endpoint(),
                    fallback.apiKey(), fallback.model(), fallback.source() + "_FALLBACK");
        }
        return environment.get(type);
    }

    public AiRuntimeConfig resolve(String type, String version) {
        validateType(type);
        if (!StringUtils.hasText(version)) return current(type);
        if ("env".equals(version) || !DYNAMIC_TYPES.contains(type)) {
            return environment.get(type);
        }
        Map<Object, Object> values = redis.opsForHash().entries(versionKey(type, version));
        if (values.isEmpty()) {
            throw new IllegalStateException("任务固定的 AI 配置版本不存在: " + type + "/" + version);
        }
        return new AiRuntimeConfig(type, version,
                string(values.get("endpoint")),
                crypto.decrypt(string(values.get("apiKey"))),
                string(values.get("model")),
                "REDIS");
    }

    @Transactional
    public AiRuntimeConfig save(String type, String endpoint, String apiKey, String model) {
        validateType(type);
        if (!DYNAMIC_TYPES.contains(type)) {
            throw new IllegalArgumentException("Embedding 配置仅允许通过服务器环境变量修改");
        }
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        if (!StringUtils.hasText(model)) throw new IllegalArgumentException("模型名称不能为空");

        AiRuntimeConfig previous = current(type);
        String effectiveKey = StringUtils.hasText(apiKey) ? apiKey : previous.apiKey();
        if (!StringUtils.hasText(effectiveKey)) throw new IllegalArgumentException("API Key 不能为空");
        if (!crypto.isConfigured()) {
            throw new IllegalStateException("请先在服务器配置 AI_CONFIG_ENCRYPTION_KEY");
        }

        Long sequence = redis.opsForValue().increment(ROOT + type + ":sequence");
        String version = sequence + "-" + Instant.now().toEpochMilli();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("endpoint", normalizedEndpoint);
        values.put("apiKey", crypto.encrypt(effectiveKey));
        values.put("model", model.trim());
        values.put("createdAt", Instant.now().toString());
        redis.opsForHash().putAll(versionKey(type, version), values);
        redis.opsForValue().set(ROOT + type + ":active", version);
        return resolve(type, version);
    }

    public Map<String, Object> describe(String type) {
        AiRuntimeConfig config = current(type);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", config.endpoint());
        result.put("apiKey", mask(config.apiKey()));
        result.put("model", config.model());
        result.put("version", config.version());
        result.put("source", config.source());
        result.put("dynamic", DYNAMIC_TYPES.contains(type));
        result.put("encryptionReady", crypto.isConfigured());
        return result;
    }

    private AiRuntimeConfig env(String type, String endpoint, String key, String model) {
        return new AiRuntimeConfig(type, "env", normalizeEndpoint(endpoint), key, model, "ENV");
    }

    private AiRuntimeConfig envWithFallback(String type,
                                            String endpoint, String key, String model,
                                            String fallbackEndpoint, String fallbackKey,
                                            String fallbackModel) {
        return env(type,
                StringUtils.hasText(endpoint) ? endpoint : fallbackEndpoint,
                StringUtils.hasText(key) ? key : fallbackKey,
                StringUtils.hasText(model) ? model : fallbackModel);
    }

    private boolean hasDedicatedEnvironment(String type) {
        AiRuntimeConfig config = environment.get(type);
        AiRuntimeConfig trend = environment.get(TREND);
        return !config.endpoint().equals(trend.endpoint())
                || !config.apiKey().equals(trend.apiKey())
                || !config.model().equals(trend.model());
    }

    private String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) return "";
        String value = endpoint.trim();
        if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) value = "https://" + value;
        URI uri = URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("AI Endpoint 格式无效");
        }
        if (production && (!"https".equalsIgnoreCase(uri.getScheme()) || !UrlValidator.isSafe(value))) {
            throw new IllegalArgumentException("生产环境 AI Endpoint 必须是安全的公网 HTTPS 地址");
        }
        return value.replaceAll("/+$", "");
    }

    private String versionKey(String type, String version) {
        return ROOT + type + ":version:" + version;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String mask(String key) {
        if (!StringUtils.hasText(key)) return "未配置";
        return key.length() <= 4 ? "****" : "********" + key.substring(key.length() - 4);
    }

    private void validateType(String type) {
        if (!environment.containsKey(type)) throw new IllegalArgumentException("AI 配置类型无效");
    }
}
