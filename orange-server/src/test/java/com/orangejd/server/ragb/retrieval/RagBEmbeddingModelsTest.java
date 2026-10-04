package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagBEmbeddingModelsTest {
    @Test void isolatesM3AndRejectsMismatchedGenerationDimensions() {
        AiEmbeddingProperties platform=new AiEmbeddingProperties();
        EmbeddingModel legacy=mock(EmbeddingModel.class);
        var models=new RagBEmbeddingModels(legacy,new RestTemplate(),platform);
        var old=new RagBIndexGeneration();old.setEmbeddingModel("bge-small-zh-v1.5");old.setEmbeddingDimensions(512);
        var modern=new RagBIndexGeneration();modern.setEmbeddingModel("bge-m3");modern.setEmbeddingDimensions(1024);
        assertThat(models.forGeneration(old)).isSameAs(legacy);
        assertThat(models.forGeneration(modern).dimensions()).isEqualTo(1024);
        assertThat(platform.getModel()).isEqualTo("bge-small-zh-v1.5");
        modern.setEmbeddingDimensions(512);
        assertThatThrownBy(()->models.forGeneration(modern)).hasMessageContaining("维度");
        verifyNoInteractions(legacy);
    }
}
