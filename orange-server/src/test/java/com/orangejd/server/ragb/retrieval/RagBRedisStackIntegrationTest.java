package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagBRedisStackIntegrationTest {
    @Test
    void indexesAndSearchesThroughRealRedisStackHnswAndTextIndex() {
        String host = System.getenv().getOrDefault("RAG_B_REDIS_TEST_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("RAG_B_REDIS_TEST_PORT", "6380"));
        try (JedisPooled jedis = new JedisPooled(host, port)) {
            try { assertThat(jedis.ping()).isEqualTo("PONG"); }
            catch (RuntimeException unavailable) {
                if (Boolean.parseBoolean(System.getenv("RAG_B_REDIS_REQUIRED"))) throw unavailable;
                Assumptions.abort("Redis Stack test endpoint is unavailable");
            }

            String suffix = UUID.randomUUID().toString().replace("-", "");
            String generationKey = "it-" + suffix;
            String prefix = "orange:rag:b:it:" + suffix + ":";
            String indexName = "orange-rag-b-" + generationKey;
            FakeEmbeddingModel model = new FakeEmbeddingModel();
            RedisVectorStore platform = RedisVectorStore.builder(jedis, model)
                    .indexName("unused-" + suffix).initializeSchema(false).build();
            @SuppressWarnings("unchecked") ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(platform);
            RagBProperties properties = new RagBProperties();
            RagBRedisStackRepository repository = new RagBRedisStackRepository(provider, model,
                    new ObjectMapper(), properties, new RagBConstraintMatcher());

            RagBIndexGeneration generation = new RagBIndexGeneration();
            generation.setId(Math.abs((long) suffix.hashCode()) + 1);
            generation.setGenerationKey(generationKey);
            generation.setRedisPrefix(prefix);
            generation.setAdapterType("HNSW_HYBRID");
            RagBDocument source = new RagBDocument();
            source.setId(1L);
            source.setTitle("Demo");
            source.setSourceType("GITHUB");
            source.setSourceRef("acme/demo");
            List<RagBChunk> chunks = new ArrayList<>();
            chunks.add(chunk(11, "Java Spring Boot deployment guide", "部署"));
            chunks.add(chunk(12, "Vue component styling handbook", "前端"));
            for (long id = 13; id <= 110; id++) chunks.add(chunk(id, "general project note " + id, "附录"));
            try {
                Map<Long, String> keys = repository.index(source, generation, chunks);
                RagBVectorSearchResult result = repository.searchWithTrace(generation,
                        "Java deployment", 4, 0, RagBSearchConstraints.sources(java.util.Set.of("GITHUB")));

                assertThat(keys).hasSize(100);
                assertThat(result.hits()).isNotEmpty();
                assertThat(result.hits().getFirst().chunkId())
                        .as("hybrid hits: %s", result.hits())
                        .isEqualTo(11L);
                assertThat(result.hits().getFirst().retrievalRoute()).startsWith("HYBRID_RRF");

                RagBDocument scopedSource=new RagBDocument(); scopedSource.setId(2L); scopedSource.setTitle("Scoped");
                scopedSource.setSourceType("GITHUB"); scopedSource.setSourceRef("acme/scoped");
                var scopedChunk=chunk(201,"Java deployment requirements for the selected project","Deploy");
                repository.index(scopedSource,generation,List.of(scopedChunk)); chunks.add(scopedChunk);
                var scoped=repository.searchWithTrace(generation,"Java deployment",4,0,RagBSearchConstraints.sources(java.util.Set.of("GITHUB")).documents(java.util.Set.of(2L)));
                assertThat(scoped.hits()).isNotEmpty().allMatch(h->h.documentId()==2L);
                var absent=repository.searchWithTrace(generation,"Java deployment",4,0,RagBSearchConstraints.sources(java.util.Set.of("GITHUB")).documents(java.util.Set.of(999L)));
                assertThat(absent.hits()).isEmpty();

                var executor = Executors.newFixedThreadPool(8);
                try {
                    List<Callable<Long>> work = java.util.stream.IntStream.range(0, 40).mapToObj(index -> (Callable<Long>) () -> {
                        long started = System.nanoTime();
                        RagBVectorSearchResult concurrent = repository.searchWithTrace(generation,
                                index % 2 == 0 ? "Java deployment" : "Vue styling", 8, 0,
                                RagBSearchConstraints.sources(java.util.Set.of("GITHUB")));
                        assertThat(concurrent.hits()).isNotEmpty();
                        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    }).toList();
                    List<Long> latencies = executor.invokeAll(work).stream().map(future -> {
                        try { return future.get(); }
                        catch (Exception ex) { throw new IllegalStateException(ex); }
                    }).sorted().toList();
                    long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
                    System.out.printf("RAG_B_REDIS_STACK_P95_MS=%d%n", p95);
                    assertThat(p95).isLessThan(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                } finally {
                    executor.shutdownNow();
                }
            } finally {
                jedis.del(chunks.stream().map(chunk -> prefix + chunk.getId()).toArray(String[]::new));
                try { jedis.ftDropIndex(indexName); } catch (RuntimeException ignored) { }
            }
        }
    }

    private RagBChunk chunk(long id, String content, String section) {
        RagBChunk chunk = new RagBChunk();
        chunk.setId(id);
        chunk.setVersionId(2L);
        chunk.setChunkIndex((int) id - 11);
        chunk.setSectionPath(section);
        chunk.setStartOffset(0);
        chunk.setEndOffset(content.length());
        chunk.setContent(content);
        chunk.setMetadataJson("{}");
        return chunk;
    }

    private static class FakeEmbeddingModel extends AbstractEmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> values = new ArrayList<>();
            List<float[]> vectors = embed(request.getInstructions());
            for (int i = 0; i < vectors.size(); i++) values.add(new Embedding(vectors.get(i), i));
            return new EmbeddingResponse(values);
        }
        @Override public List<float[]> embed(List<String> texts) { return texts.stream().map(this::vector).toList(); }
        @Override public float[] embed(String text) { return vector(text); }
        @Override public float[] embed(Document document) { return vector(document.getText()); }
        @Override public int dimensions() { return 3; }
        private float[] vector(String text) {
            String value = text == null ? "" : text.toLowerCase();
            return new float[]{value.contains("java") ? 1f : 0.1f,
                    value.contains("vue") ? 1f : 0.1f, value.contains("deploy") ? 0.8f : 0.1f};
        }
    }
}
