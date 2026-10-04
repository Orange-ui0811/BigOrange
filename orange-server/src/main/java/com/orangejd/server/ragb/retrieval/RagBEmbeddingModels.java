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

    public RagBEmbeddingModels(EmbeddingModel legacy, RestTemplate rest, AiEmbeddingProperties platform) {
        this.legacy = legacy;
        AiEmbeddingProperties isolated = new AiEmbeddingProperties();
        isolated.setEndpoint(platform.getEndpoint());
        isolated.setApiKey(platform.getApiKey());
        isolated.setModel("bge-m3");
        this.m3 = new GiteeAiEmbeddingModel(rest, isolated);
    }

    public EmbeddingModel forGeneration(RagBIndexGeneration generation) {
        if (generation.getEmbeddingModel() == null) return legacy;
        if ("bge-m3".equals(generation.getEmbeddingModel()) && Integer.valueOf(1024).equals(generation.getEmbeddingDimensions())) return m3;
        if ("bge-small-zh-v1.5".equals(generation.getEmbeddingModel()) && Integer.valueOf(512).equals(generation.getEmbeddingDimensions())) return legacy;
        throw new IllegalStateException("索引代际的 Embedding 模型与维度不受支持");
    }
}
