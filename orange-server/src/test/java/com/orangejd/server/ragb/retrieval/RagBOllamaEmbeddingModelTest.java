package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.foundation.external.ExternalCallDeadline;
import com.orangejd.server.ragb.config.RagBProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RagBOllamaEmbeddingModelTest {
    static final String DIGEST = "sha256:" + "a".repeat(64);
    static final String VECTOR = "[" + "0.03125,".repeat(1023) + "0.03125]";
    HttpServer server;
    RagBOllamaEmbeddingModel model;
    ExecutorService handlers = Executors.newCachedThreadPool();
    ExecutorService callers = Executors.newFixedThreadPool(2);
    AtomicInteger embeddings = new AtomicInteger();
    String tagDigest = DIGEST;
    String vector = VECTOR;
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    boolean slow;

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        var config = new RagBProperties();config.setEmbeddingRevision(DIGEST);
        config.setOllamaBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        model = new RagBOllamaEmbeddingModel(config);
        server.createContext("/api/tags", e -> {
            byte[] b=("{\"models\":[{\"name\":\"bge-m3:orangejd-v1\",\"digest\":\""+tagDigest+"\"}]}").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();
        });
        server.createContext("/api/embed", e -> {
            int call=embeddings.incrementAndGet();
            var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getRequestBody());
            assertThat(e.getRequestHeaders().getFirst("Authorization")).isNull();
            assertThat(body.path("truncate").asBoolean(true)).isFalse();
            if(slow && call==1) { entered.countDown();try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();} }
            String result="{\"embeddings\":["+String.join(",", java.util.Collections.nCopies(body.path("input").size(),vector))+"]}";
            byte[] b=result.getBytes(StandardCharsets.UTF_8);
            try {e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);}finally{e.close();}
        });server.start();
    }
    @AfterEach void close() {release.countDown();model.close();server.stop(0);handlers.shutdownNow();callers.shutdownNow();}

    @Test void generatesOrderedNormalizedQueryAndDocumentVectors() {
        assertThat(model.embed("query")).hasSize(1024);
        assertThat(model.embed(List.of("one","two"))).hasSize(2);
        assertThat(model.embed(new org.springframework.ai.document.Document("document"))).hasSize(1024);
        assertThat(model.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("one","two"),null)).getResults()).hasSize(2);
    }
    @Test void replacingTagCannotMixOldAndNewVectors() {
        model.embed("first");tagDigest="sha256:"+"b".repeat(64);
        assertThatThrownBy(()->model.embed("second")).hasMessage("EMBEDDING_REVISION_MISMATCH");
        assertThat(embeddings.get()).isEqualTo(1);
    }
    @Test void malformedDimensionsAndZeroNormAreRejected() {
        vector="[0.1]";
        assertThatThrownBy(()->model.embed("query")).hasMessage("EMBEDDING_DIMENSION_MISMATCH");
        vector="["+"0,".repeat(1023)+"0]";
        assertThatThrownBy(()->model.embed("query")).hasMessage("EMBEDDING_NOT_NORMALIZED");
    }
    @Test void deadlineStopsLateResponseWithoutRetry() {
        slow=true;
        try(var ignored=ExternalCallDeadline.open(Instant.now().plusMillis(200))) {
            assertThatThrownBy(()->model.embed("slow")).hasMessageContaining("EMBEDDING_");
        }
        assertThat(embeddings.get()).isLessThanOrEqualTo(1);
    }
    @Test void cancellationReleasesAdmissionForFollowingQuery() throws Exception {
        slow=true;
        var finished=new CountDownLatch(1);
        var future=callers.submit(()->{try{model.embed("slow");}finally{finished.countDown();}});
        assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();future.cancel(true);
        assertThat(finished.await(2,TimeUnit.SECONDS)).isTrue();
        assertThat(callers.submit(()->model.embed("next")).get(2,TimeUnit.SECONDS)).hasSize(1024);
    }
}
