package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ragb.config.RagBProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RagBRerankerTest {
    private HttpServer server;private RagBReranker client;
    private final AtomicInteger calls=new AtomicInteger();private final AtomicReference<String> request=new AtomicReference<>();
    private final RagBProperties properties=new RagBProperties();
    private final AiEmbeddingProperties embedding=new AiEmbeddingProperties();
    private final ObjectMapper json=new ObjectMapper();
    private volatile String reply="{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.1}]}";
    private volatile int status=200;private volatile int delay;
    private volatile java.util.function.Function<String,String> responder;
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/rerank",exchange->{
            calls.incrementAndGet();request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals("Bearer configured-test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            try{if(delay>0)Thread.sleep(delay);byte[] data=(responder==null?reply:responder.apply(request.get())).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}
        });server.start();
        embedding.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");embedding.setApiKey("configured-test-key");properties.getRerank().setEnabled(true);
    }
    private RagBReranker client(){return client=new RagBReranker(properties,embedding,json);}
    @AfterEach void cleanup(){if(client!=null)client.close();server.stop(0);Thread.interrupted();}
    private List<RagBSearchHit> hits(){return List.of(hit(1,.9,"original first"),hit(2,.8,"original second"));}
    private RagBSearchHit hit(long id,double score,String content){return new RagBSearchHit(id,1,id,0,"Tool","GITHUB","owner/tool","Usage",0,content.length(),content,score,"HYBRID_RRF");}
    @Test void appliesFullResponseAndPreservesOriginalFactsAndFusedScores()throws Exception{
        var input=hits();var result=client().rerank("install",input);
        assertEquals("APPLIED",result.trace().status());assertEquals(2,result.trace().rerankedCount());
        assertEquals(List.of(2L,1L),result.hits().stream().map(RagBSearchHit::chunkId).toList());
        assertEquals(.8,result.hits().getFirst().score());assertEquals(.9,result.hits().getFirst().rerankScore());
        assertEquals(input.get(1).content(),result.hits().getFirst().content());assertNull(input.getFirst().rerankScore());
        var body=json.readTree(request.get());assertEquals(2,body.path("top_n").asInt());assertFalse(body.path("return_documents").asBoolean());
        assertTrue(body.path("documents").get(0).asText().contains("original first"));assertFalse(request.get().contains("configured-test-key"));assertEquals(1,calls.get());
    }
    @Test void disabledAndTrivialPoolsNeverCallProvider(){
        properties.getRerank().setEnabled(false);var c=client();assertEquals("DISABLED",c.rerank("q",hits()).trace().status());
        properties.getRerank().setEnabled(true);assertEquals("EMPTY",c.rerank("q",List.of()).trace().fallbackReason());assertEquals("SINGLE_CANDIDATE",c.rerank("q",hits().subList(0,1)).trace().fallbackReason());assertEquals(0,calls.get());
    }
    @Test void duplicateMissingForeignAndNonNumericScoresFailClosed(){
        var c=client();for(String bad:List.of("{\"results\":[{\"index\":0,\"score\":1},{\"index\":0,\"score\":2}]}","{\"results\":[{\"index\":0,\"score\":1}]}","{\"results\":[{\"index\":0,\"score\":1},{\"index\":99,\"score\":2}]}","{\"results\":[{\"index\":0,\"score\":\"high\"},{\"index\":1,\"score\":2}]}")){
            reply=bad;var r=c.rerank("q",hits());assertEquals("INVALID_RESPONSE",r.trace().fallbackReason());assertEquals(hits(),r.hits());
        }assertEquals(4,calls.get());
    }
    @Test void serviceErrorAndMalformedJsonReturnOriginalRankingWithoutRetry(){
        var c=client();status=503;assertEquals("HTTP_503",c.rerank("q",hits()).trace().fallbackReason());assertEquals(1,calls.get());
        status=200;reply="not json";assertEquals(hits(),c.rerank("q",hits()).hits());assertEquals(2,calls.get());
    }
    @Test void timeoutIsBoundedAndFallsBack(){
        properties.getRerank().setTimeoutMillis(100);delay=500;long start=System.nanoTime();var r=client().rerank("q",hits());
        assertEquals("TIMEOUT",r.trace().fallbackReason());assertEquals(hits(),r.hits());assertTrue((System.nanoTime()-start)/1e6<2000);
    }
    @Test void explicitCandidateCapDoesNotDiscardUnscoredTail(){
        properties.getRerank().setMaxCandidates(2);var input=new ArrayList<>(hits());input.add(hit(3,.7,"tail"));var r=client().rerank("q",input);
        assertEquals(List.of(2L,1L,3L),r.hits().stream().map(RagBSearchHit::chunkId).toList());assertNull(r.hits().getLast().rerankScore());assertEquals(3,r.trace().candidateCount());
    }
    @Test void stableModelTiesKeepTheFusedOrder(){
        reply="{\"results\":[{\"index\":1,\"score\":0.5},{\"index\":0,\"score\":0.5}]}";
        assertEquals(List.of(1L,2L),client().rerank("q",hits()).hits().stream().map(RagBSearchHit::chunkId).toList());
    }
    @Test void missingCredentialOrOversizedInputMakesNoRequest(){
        var c=client();embedding.setApiKey("");assertEquals("MISSING_KEY",c.rerank("q",hits()).trace().fallbackReason());
        embedding.setApiKey("configured-test-key");properties.getRerank().setMaxRequestCharacters(1000);
        assertEquals("REQUEST_BUDGET",c.rerank("q",List.of(hit(1,.9,"x".repeat(1001)),hit(2,.8,"other"))).trace().fallbackReason());assertEquals(0,calls.get());
    }
    @Test void cannotForwardEmbeddingCredentialToAnotherOrigin(){
        properties.getRerank().setEndpoint("http://localhost:"+server.getAddress().getPort()+"/v1");
        assertEquals("MISSING_DEDICATED_KEY",client().rerank("q",hits()).trace().fallbackReason());assertEquals(0,calls.get());
    }
    @Test void concurrentOverflowFallsBackWithoutWaiting()throws Exception{
        properties.getRerank().setMaxConcurrentRequests(1);delay=250;var c=client();var pool=Executors.newSingleThreadExecutor();
        try{var first=pool.submit(()->c.rerank("q",hits()));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(calls.get()==0&&System.nanoTime()<end)Thread.sleep(5);
            assertEquals(1,calls.get());assertEquals("BUSY",c.rerank("q",hits()).trace().fallbackReason());assertEquals("APPLIED",first.get(3,TimeUnit.SECONDS).trace().status());assertEquals(1,calls.get());
        }finally{pool.shutdownNow();}
    }
    @Test void interruptionIsPreservedWithoutProviderCall(){
        var c=client();Thread.currentThread().interrupt();assertEquals("INTERRUPTED",c.rerank("q",hits()).trace().fallbackReason());assertTrue(Thread.currentThread().isInterrupted());assertEquals(0,calls.get());
    }
    private List<RagBSearchHit> fourHits(){var input=new ArrayList<>(hits());input.add(hit(3,.7,"third fact"));input.add(hit(4,.6,"fourth fact"));return input;}
    @Test void mergesLocalBatchIndexesIntoOneGlobalRanking(){
        properties.getRerank().setBatchSize(2);
        responder=body->body.contains("third fact")?"{\"results\":[{\"index\":0,\"score\":0.9},{\"index\":1,\"score\":0.2}]}":"{\"results\":[{\"index\":0,\"score\":0.1},{\"index\":1,\"score\":0.4}]}";
        var r=client().rerank("q",fourHits());assertEquals("APPLIED",r.trace().status());assertEquals(2,r.trace().requestCount());
        assertEquals(List.of(3L,2L,4L,1L),r.hits().stream().map(RagBSearchHit::chunkId).toList());assertEquals(4,r.trace().rerankedCount());assertEquals(2,calls.get());
    }
    @Test void laterBatchFailureDiscardsEarlierScores(){
        properties.getRerank().setBatchSize(2);responder=body->body.contains("third fact")?"{\"results\":[]}":reply;
        var input=fourHits();var r=client().rerank("q",input);assertEquals("INVALID_RESPONSE",r.trace().fallbackReason());assertEquals(input,r.hits());assertEquals(2,r.trace().requestCount());assertTrue(r.hits().stream().allMatch(h->h.rerankScore()==null));
    }
    @Test void batchesShareOneTimeoutBudget(){
        properties.getRerank().setBatchSize(2);properties.getRerank().setTimeoutMillis(350);delay=250;
        long start=System.nanoTime();var input=fourHits();var r=client().rerank("q",input);
        assertEquals("TIMEOUT",r.trace().fallbackReason());assertEquals(input,r.hits());assertTrue((System.nanoTime()-start)/1e6<1000);assertTrue(r.trace().requestCount()<=2);
    }
}
