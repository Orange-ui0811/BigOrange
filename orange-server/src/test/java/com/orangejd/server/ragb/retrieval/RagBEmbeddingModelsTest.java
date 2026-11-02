package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagBEmbeddingModelsTest {
    @Test void providerIsBoundToGenerationRatherThanTheCurrentDefault() {
        var platform = new AiEmbeddingProperties();
        var props = new com.orangejd.server.ragb.config.RagBProperties();
        props.setEmbeddingProvider("OLLAMA");props.setEmbeddingRevision("sha256:"+"a".repeat(64));
        var models = new RagBEmbeddingModels(mock(EmbeddingModel.class),new RestTemplate(),platform,props);
        try {
            var old = new RagBIndexGeneration();old.setEmbeddingModel("bge-m3");old.setEmbeddingDimensions(1024);
            assertThat(models.forGeneration(old)).isInstanceOf(com.orangejd.server.ai.GiteeAiEmbeddingModel.class);
            var local = new RagBIndexGeneration();local.setEmbeddingModel("bge-m3");local.setEmbeddingDimensions(1024);
            local.setEmbeddingProvider("OLLAMA");local.setEmbeddingRevision(props.getEmbeddingRevision());
            assertThat(models.forGeneration(local)).isInstanceOf(RagBOllamaEmbeddingModel.class);
            local.setEmbeddingRevision("sha256:"+"b".repeat(64));
            assertThatThrownBy(()->models.forGeneration(local)).hasMessage("EMBEDDING_REVISION_MISMATCH");
        } finally { models.close(); }
    }
    @Test void documentEmbeddingStillUsesTheOriginalBatchTransport() {
        var platform=new AiEmbeddingProperties();platform.setEndpoint("https://synthetic.invalid/v1");
        var rest=new RestTemplate();var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://synthetic.invalid/v1/embeddings"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"data\":[{\"index\":0,\"embedding\":[0.1]}]}",org.springframework.http.MediaType.APPLICATION_JSON));
        var models=new RagBEmbeddingModels(mock(EmbeddingModel.class),rest,platform);
        try {
            var generation=new RagBIndexGeneration();generation.setEmbeddingModel("bge-m3");generation.setEmbeddingDimensions(1024);
            assertThat(models.forGeneration(generation).embed(new org.springframework.ai.document.Document("source document"))).hasSize(1);
            server.verify();
        } finally {models.close();}
    }
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
