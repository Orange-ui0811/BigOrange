package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.ExternalCallDeadline;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.service.TrendLabObservation;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Spring AI adapter with interruptible Ollama I/O; no retries or global model bean changes. */
final class RagBOllamaEmbeddingModel extends AbstractEmbeddingModel implements AutoCloseable {
    private final RagBProperties config;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Semaphore admission = new Semaphore(1, true);

    RagBOllamaEmbeddingModel(RagBProperties config) { this.config = config; }

    void requireRevision(String revision) {
        if (revision == null || !revision.matches("sha256:[a-f0-9]{64}") || !revision.equals(config.getEmbeddingRevision()))
            throw new IllegalStateException("EMBEDDING_REVISION_MISMATCH");
    }

    @Override public int dimensions() { return 1024; }
    @Override public float[] embed(Document document) { return embed(List.of(document.getText())).getFirst(); }
    @Override public float[] embed(String text) {
        return TrendLabObservation.measure("B.queryEmbedding", () -> request(List.of(text), 12).getFirst());
    }
    @Override public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        return TrendLabObservation.measure("embedding.http.bge-m3.ollama", () -> request(texts, 60));
    }
    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        var vectors = embed(request.getInstructions());
        var results = new ArrayList<Embedding>();
        for (int i = 0; i < vectors.size(); i++) results.add(new Embedding(vectors.get(i), i));
        return new EmbeddingResponse(results);
    }

    private List<float[]> request(List<String> texts, int seconds) {
        requireRevision(config.getEmbeddingRevision());
        Instant deadline = ExternalCallDeadline.bound(Instant.now().plusSeconds(seconds));
        boolean acquired = false;
        try {
            acquired = admission.tryAcquire(remaining(deadline), TimeUnit.MILLISECONDS);
            if (!acquired) throw new IllegalStateException("EMBEDDING_ADMISSION_TIMEOUT");
            // Re-check the tag before every call so replacing a local model cannot silently mix vector spaces.
            JsonNode tags = send("/api/tags", null, deadline, 3000).path("models");
            boolean pinned = false;
            for (JsonNode tag : tags) {
                if (config.getOllamaModel().equals(tag.path("name").asText())
                        && config.getEmbeddingRevision().replaceFirst("^sha256:", "")
                            .equals(tag.path("digest").asText().replaceFirst("^sha256:", ""))) pinned = true;
            }
            if (!pinned) throw new IllegalStateException("EMBEDDING_REVISION_MISMATCH");
            var body = Map.of("model", config.getOllamaModel(), "input", texts,
                    "truncate", false, "keep_alive", -1);
            com.orangejd.server.foundation.external.AiAcceptanceBudget.beforeRequest("OLLAMA_EMBEDDING");
            JsonNode response = send("/api/embed", json.writeValueAsString(body), deadline, seconds == 12 ? 8000 : 60000);
            JsonNode vectors = response.path("embeddings");
            if (!vectors.isArray() || vectors.size() != texts.size())
                throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
            var result = new ArrayList<float[]>();
            for (JsonNode vector : vectors) {
                if (!vector.isArray() || vector.size() != dimensions())
                    throw new IllegalStateException("EMBEDDING_DIMENSION_MISMATCH");
                float[] values = new float[dimensions()];
                double norm = 0;
                for (int i = 0; i < values.length; i++) {
                    if (!vector.get(i).isNumber()) throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
                    values[i] = (float) vector.get(i).asDouble();
                    if (!Float.isFinite(values[i])) throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
                    norm += (double) values[i] * values[i];
                }
                if (norm < 0.99 || norm > 1.01) throw new IllegalStateException("EMBEDDING_NOT_NORMALIZED");
                result.add(values);
            }
            remaining(deadline); // A late response must not flow into tools or result submission.
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RUN_STOPPED", ex);
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("EMBEDDING_TIMEOUT", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("EMBEDDING_IO_FAILED", ex);
        } finally { if (acquired) admission.release(); }
    }

    private JsonNode send(String path, String body, Instant deadline, long maxMillis) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(config.getOllamaBaseUrl().replaceAll("/$", "") + path))
                .timeout(Duration.ofMillis(Math.min(maxMillis, remaining(deadline))));
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("EMBEDDING_HTTP_" + response.statusCode());
        remaining(deadline);
        return json.readTree(response.body());
    }
    private static long remaining(Instant deadline) {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("RUN_STOPPED");
        long value = Duration.between(Instant.now(), deadline).toMillis();
        if (value <= 0) throw new IllegalStateException("EMBEDDING_DEADLINE_EXCEEDED");
        return value;
    }
    @Override public void close() { client.shutdownNow(); }
}
