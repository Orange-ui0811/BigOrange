package com.orangejd.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Embedding 配置属性
 * <p>
 * YAML 前缀：{@code orange.ai-embedding}
 * <p>
 * 环境变量覆盖：{@code AI_EMBEDDING_ENDPOINT}, {@code AI_EMBEDDING_API_KEY}, {@code AI_EMBEDDING_MODEL}
 */
@ConfigurationProperties(prefix = "orange.ai-embedding")
public class AiEmbeddingProperties {

    /** Gitee AI embedding endpoint（OpenAI 兼容格式） */
    private String endpoint = "https://ai.gitee.com/v1";

    /** API Key */
    private String apiKey = "";

    /** Embedding 模型名 */
    private String model = "bge-small-zh-v1.5";

    // ---- getters / setters ----

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
