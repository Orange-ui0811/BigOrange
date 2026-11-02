package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.foundation.external.ExternalCallDeadline;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RagBQueryEmbeddingTest {
    HttpServer server;
    RagBQueryEmbedding embedding;
    ExecutorService handlers = Executors.newCachedThreadPool();
    ExecutorService callers = Executors.newFixedThreadPool(2);
    static final String VECTOR = "{\"data\":[{\"index\":0,\"embedding\":[" + "0.1,".repeat(1023) + "0.1]}]}";

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(handlers);
        var config = new AiEmbeddingProperties();config.setModel("bge-m3");config.setApiKey("synthetic-test-key");
        config.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort());
        embedding = new RagBQueryEmbedding(config);
    }
    @AfterEach void close() { embedding.close();server.stop(0);handlers.shutdownNow();callers.shutdownNow(); }

    @Test void preservesProviderContractAndValidatesDimensions() throws Exception {
        var requests = new AtomicInteger();
        server.createContext("/embeddings", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertThat(body).contains("bge-m3","query");
            byte[] response = (requests.incrementAndGet()==1?VECTOR:"{\"data\":[{\"index\":0,\"embedding\":[0.1]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        assertThat(embedding.embed("query")).hasSize(1024);
        assertThatThrownBy(()->embedding.embed("query")).hasMessage("EMBEDDING_DIMENSION_MISMATCH");
    }
    @Test void rateLimitDoesNotRetryOrSendFollowingRequestsDuringCooldown() {
        var requests = new AtomicInteger();
        server.createContext("/embeddings", exchange -> {
            requests.incrementAndGet();exchange.getResponseHeaders().add("Retry-After","60");
            exchange.sendResponseHeaders(429,-1);exchange.close();
        });server.start();
        assertThatThrownBy(()->embedding.embed("query")).hasMessage("EMBEDDING_RATE_LIMITED");
        assertThatThrownBy(()->embedding.embed("different query")).hasMessage("EMBEDDING_COOLDOWN");
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void cancellationReleasesAdmissionAndDoesNotStrandNextRequest() throws Exception {
        var entered = new CountDownLatch(1);var release = new CountDownLatch(1);var finished = new CountDownLatch(1);
        var requests = new AtomicInteger();
        server.createContext("/embeddings", exchange -> {
            if(requests.incrementAndGet()==1) {entered.countDown();try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
            try {byte[] bytes=VECTOR.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });server.start();
        var first = callers.submit(()->{try {embedding.embed("first");}finally{finished.countDown();}});
        assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
        first.cancel(true);
        assertThat(finished.await(2,TimeUnit.SECONDS)).isTrue();
        assertThat(callers.submit(()->embedding.embed("second")).get(2,TimeUnit.SECONDS)).hasSize(1024);
        release.countDown();assertThat(requests.get()).isEqualTo(2);
    }
    @Test void httpDeadlineAndScopeAreBoundedAndCleaned() {
        server.createContext("/embeddings", exchange -> {
            try {Thread.sleep(2000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally {exchange.close();}
        });server.start();
        Instant started=Instant.now();
        try(var scope=ExternalCallDeadline.open(started.plusMillis(500))) {
            assertThatThrownBy(()->embedding.embed("query")).hasMessage("EMBEDDING_TIMEOUT");
        }
        assertThat(java.time.Duration.between(started,Instant.now()).toMillis()).isLessThan(1600);
        Instant next=Instant.now().plusSeconds(10);
        assertThat(ExternalCallDeadline.bound(next)).isEqualTo(next);
    }
}
