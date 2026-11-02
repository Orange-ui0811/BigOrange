package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only native B retrieval; schedules/startup recovery disabled, no corpus rebuild. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_RERANK_SNAPSHOT",matches="true")
class RagBRerankerSnapshotTest {
    @MockBean com.orangejd.server.customer.CustomerRuns disabledRecovery;
    @MockBean com.orangejd.server.service.AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired RagBSearchService search;
    @Autowired RagBProperties properties;
    @Autowired RagBMapper mapper;
    private final ObjectMapper json=new ObjectMapper();
    @Test void captureNativeBaselineAndTwentyCandidates()throws Exception{
        Path root=Path.of(System.getProperty("rerank.root"));Path out=root.resolve("docs/rag-b/reranker-v1");
        assertFalse(Files.exists(out.resolve("capture-started.json")),"Preserve any partial capture");
        assertEquals("OLLAMA",properties.getEmbeddingProvider());
        var generation=mapper.selectActiveGeneration();assertNotNull(generation);assertEquals("OLLAMA",generation.getEmbeddingProvider());
        assertEquals("HNSW_HYBRID",generation.getAdapterType());assertEquals("STRUCTURE_M3_512_768_64_V1",generation.getChunkPolicy());
        assertEquals(properties.getEmbeddingRevision(),generation.getEmbeddingRevision());
        long chunkRows=mapper.countGenerationChunks(generation.getId());int docs=mapper.selectReadyDocuments().size();assertTrue(chunkRows>0);
        Files.writeString(out.resolve("capture-started.json"),json.writeValueAsString(Map.of("generation",generation,"readyDocuments",docs,"generationChunkRows",chunkRows,"cloudCalls",0)));
        var cases=json.readTree(Files.readString(out.resolve("cases.json"))).path("cases");assertEquals(24,cases.size());
        List<Object> rows=new ArrayList<>();
        for(var c:cases){
            String query=c.path("question").asText();RagBSearchConstraints constraints=RagBSearchConstraints.sources(Set.of());
            if(c.has("rule"))constraints=json.treeToValue(c.path("rule"),RagBEvaluationService.EvaluationCase.class).constraints();
            long started=System.nanoTime();var baseline=search.retrieve(query,8,0.55,constraints);double baseMs=(System.nanoTime()-started)/1e6;
            started=System.nanoTime();var pool=search.retrieve(query,20,0.55,constraints);double poolMs=(System.nanoTime()-started)/1e6;
            assertEquals(generation.getGenerationKey(),baseline.generation());assertEquals(baseline.generation(),pool.generation());
            Set<Long> ids=new HashSet<>();pool.hits().forEach(h->ids.add(h.chunkId()));assertTrue(baseline.hits().stream().allMatch(h->ids.contains(h.chunkId())),"Candidate pool must contain native baseline");
            rows.add(Map.of("key",c.path("key").asText(),"baseline",baseline,"pool",pool,"baselineMs",baseMs,"poolMs",poolMs));
            Files.writeString(out.resolve("candidate-snapshot.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(rows)+"\n");
        }
        assertEquals(generation.getId(),mapper.selectActiveGeneration().getId());assertEquals(chunkRows,mapper.countGenerationChunks(generation.getId()));assertEquals(docs,mapper.selectReadyDocuments().size());
        Files.writeString(out.resolve("capture-completion.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("cases",24,"retrievalQueries",48,"localEmbeddingOnly",true,"corpusWrites",0,"generationUnchanged",true,"maxReturnedPool",20))+"\n");
    }
}
