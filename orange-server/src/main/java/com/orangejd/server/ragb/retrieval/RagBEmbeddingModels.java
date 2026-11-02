package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.ai.GiteeAiEmbeddingModel;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** B generations carry their own embedding contract; A configuration is never mutated. */
@Component
public class RagBEmbeddingModels {
    private final EmbeddingModel legacy;
    private final EmbeddingModel m3;
    private final RagBQueryEmbedding query;
    private final RagBOllamaEmbeddingModel local;

    public RagBEmbeddingModels(EmbeddingModel legacy, RestTemplate rest, AiEmbeddingProperties platform) {
        this(legacy, rest, platform, new com.orangejd.server.ragb.config.RagBProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RagBEmbeddingModels(EmbeddingModel legacy, RestTemplate rest, AiEmbeddingProperties platform,
                              com.orangejd.server.ragb.config.RagBProperties properties) {
        this.legacy = legacy;
        this.local = new RagBOllamaEmbeddingModel(properties);
        AiEmbeddingProperties isolated = new AiEmbeddingProperties();
        isolated.setEndpoint(platform.getEndpoint());
        isolated.setApiKey(platform.getApiKey());
        isolated.setModel("bge-m3");
        this.query = new RagBQueryEmbedding(isolated);
        this.m3 = new GiteeAiEmbeddingModel(rest, isolated) {
            @Override public float[] embed(String text) { return query.embed(text); }
            // Document ingestion retains its batch transport and timeout; only searches use the query budget.
            @Override public float[] embed(org.springframework.ai.document.Document document) {
                return super.embed(java.util.List.of(document.getText())).getFirst();
            }
        };
    }

    public EmbeddingModel forGeneration(RagBIndexGeneration generation) {
        if (generation.getEmbeddingModel() == null) return legacy;
        if ("bge-m3".equals(generation.getEmbeddingModel()) && Integer.valueOf(1024).equals(generation.getEmbeddingDimensions())) {
            String provider = generation.getEmbeddingProvider() == null ? "GITEE" : generation.getEmbeddingProvider();
            if ("GITEE".equals(provider)) return m3;
            if ("OLLAMA".equals(provider)) {
                local.requireRevision(generation.getEmbeddingRevision());
                return local;
            }
            throw new IllegalStateException("B Embedding provider is unsupported");
        }
        if ("bge-small-zh-v1.5".equals(generation.getEmbeddingModel()) && Integer.valueOf(512).equals(generation.getEmbeddingDimensions())) return legacy;
        throw new IllegalStateException("索引代际的 Embedding 模型与维度不受支持");
    }
    @jakarta.annotation.PreDestroy public void close() { query.close(); local.close(); }
}
