package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.foundation.external.ExternalCallDeadline;
import com.orangejd.server.service.TrendLabObservation;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Query-only transport: interruptible I/O, shared admission and provider cooldown, no hidden retries. */
final class RagBQueryEmbedding implements AutoCloseable {
    private final AiEmbeddingProperties config;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Semaphore permits = new Semaphore(1, true);
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(Instant.MIN);

    RagBQueryEmbedding(AiEmbeddingProperties config) { this.config = config; }

    float[] embed(String query) {
        return TrendLabObservation.measure("B.queryEmbedding", () -> request(query));
    }

    private float[] request(String query) {
        Instant deadline = ExternalCallDeadline.bound(Instant.now().plusSeconds(12));
        boolean acquired = false;
        try {
            check(deadline);
            acquired = permits.tryAcquire(remainingMillis(deadline), TimeUnit.MILLISECONDS);
            if (!acquired) throw new IllegalStateException("EMBEDDING_ADMISSION_TIMEOUT");
            check(deadline); // A preceding request may have started provider cooldown.
            var body = json.writeValueAsString(Map.of("model", config.getModel(), "input", List.of(query)));
            var request = HttpRequest.newBuilder(URI.create(config.getEndpoint().replaceAll("/$", "") + "/embeddings"))
                    .timeout(Duration.ofMillis(Math.min(8000, remainingMillis(deadline))))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                Instant until = retryAfter(response.headers().firstValue("Retry-After").orElse(""));
                blockedUntil.accumulateAndGet(until, (a,b) -> a.isAfter(b) ? a : b);
                throw new IllegalStateException("EMBEDDING_RATE_LIMITED");
            }
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("EMBEDDING_HTTP_" + response.statusCode());
            check(deadline);
            var data = json.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != 1 || data.get(0).path("index").asInt(-1) != 0)
                throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
            var vector = data.get(0).path("embedding");
            if (!vector.isArray() || vector.size() != 1024)
                throw new IllegalStateException("EMBEDDING_DIMENSION_MISMATCH");
            float[] result = new float[vector.size()];
            for (int i=0; i<result.length; i++) {
                if (!vector.get(i).isNumber()) throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
                result[i] = (float) vector.get(i).asDouble();
                if (!Float.isFinite(result[i])) throw new IllegalStateException("EMBEDDING_INVALID_RESPONSE");
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RUN_STOPPED", ex);
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("EMBEDDING_TIMEOUT", ex);
        } catch (IOException ex) {
            // Never include provider bodies or headers in error messages.
            throw new IllegalStateException("EMBEDDING_IO_FAILED", ex);
        } finally {
            if (acquired) permits.release();
        }
    }

    private void check(Instant deadline) {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("RUN_STOPPED");
        remainingMillis(deadline);
        if (Instant.now().isBefore(blockedUntil.get())) throw new IllegalStateException("EMBEDDING_COOLDOWN");
    }
    private static long remainingMillis(Instant deadline) {
        long remaining = Duration.between(Instant.now(), deadline).toMillis();
        if (remaining <= 0) throw new IllegalStateException("EMBEDDING_DEADLINE_EXCEEDED");
        return remaining;
    }
    private static Instant retryAfter(String value) {
        Instant fallback = Instant.now().plusSeconds(30);
        try {
            Instant requested = value.matches("\\d+") ? Instant.now().plusSeconds(Long.parseLong(value))
                    : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return requested.isAfter(fallback) ? requested : fallback;
        } catch (RuntimeException ex) { return fallback; }
    }
    @Override public void close() { client.shutdownNow(); }
}
