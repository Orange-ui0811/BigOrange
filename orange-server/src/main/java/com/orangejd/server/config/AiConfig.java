package com.orangejd.server.config;

import com.orangejd.server.ai.GiteeAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Spring AI 配置
 */
@Configuration
@EnableConfigurationProperties({AiEmbeddingProperties.class, EpayProperties.class})
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);
    private static final int EMBEDDING_TIMEOUT_MS = 30000;

    private final AiEmbeddingProperties properties;

    @Autowired
    private ApplicationContext ctx;

    public AiConfig(AiEmbeddingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void reportStatus() {
        log.info("AI Embedding 模型: {} ({})", properties.getModel(), properties.getEndpoint());
        boolean hasVectorStore = ctx.getBeanNamesForType(
                org.springframework.ai.vectorstore.VectorStore.class).length > 0;
        if (hasVectorStore) {
            log.info("RedisStackVectorStore 已就绪 — RAG 模式可用");
        } else {
            log.warn("RedisStackVectorStore 未就绪 — 应用正常启动但 RAG 不可用");
        }
    }

    @Bean
    public RestTemplate embeddingRestTemplate() {
        RestTemplate t = new RestTemplate();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(EMBEDDING_TIMEOUT_MS);
        f.setReadTimeout(EMBEDDING_TIMEOUT_MS);
        t.setRequestFactory(f);
        return t;
    }

    @Bean
    public EmbeddingModel embeddingModel(RestTemplate embeddingRestTemplate) {
        return new GiteeAiEmbeddingModel(embeddingRestTemplate, properties);
    }
}
