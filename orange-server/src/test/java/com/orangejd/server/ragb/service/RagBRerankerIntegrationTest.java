package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_RERANK_INTEGRATION",matches="true")
class RagBRerankerIntegrationTest {
    @MockBean com.orangejd.server.customer.CustomerRuns disabledRecovery;
    @MockBean com.orangejd.server.service.AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired RagBSearchService search;
    @Autowired RagBProperties properties;
    @Autowired RagBMapper mapper;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void validatesRealRerankingBeforeNativeTopK()throws Exception{
        Path root=Path.of(System.getProperty("rerank.root")),old=root.resolve("docs/rag-b/reranker-v1"),out=root.resolve("docs/rag-b/reranker-integration-v1");
        assertFalse(Files.exists(out.resolve("live-started.json")));assertTrue(properties.getRerank().isEnabled());assertEquals("OLLAMA",properties.getEmbeddingProvider());
        assertEquals(80,properties.getRerank().getMaxCandidates());assertEquals(20,properties.getRerank().getBatchSize());
        var manifest=json.readTree(Files.readString(out.resolve("manifest.json"))).path("files");
        for(var it=manifest.fields();it.hasNext();){var e=it.next();assertEquals(e.getValue().asText(),com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.sha256(Files.readString(root.resolve(e.getKey()))));}
        var generation=mapper.selectActiveGeneration();var oldGeneration=json.readTree(Files.readString(old.resolve("capture-started.json")));
        assertEquals(oldGeneration.path("generation").path("generationKey").asText(),generation.getGenerationKey());
        assertEquals(oldGeneration.path("generationChunkRows").asLong(),mapper.countGenerationChunks(generation.getId()));
        var allCases=json.readTree(Files.readString(old.resolve("cases.json"))).path("cases");List<JsonNode> cases=new ArrayList<>();
        Set<String> selected=Set.of("fact-00-zh","fact-20-zh","fact-21-zh","fact-24-zh","github-langgraph","vue-no-docker","refuse-ruby-career","refuse-commercial-price");
        allCases.forEach(c->{if(selected.contains(c.path("key").asText()))cases.add(c);});assertEquals(8,cases.size());Map<String,JsonNode> baselines=new HashMap<>();
        for(var row:json.readTree(Files.readString(old.resolve("candidate-snapshot.json"))))baselines.put(row.path("key").asText(),row.path("baseline"));
        Files.writeString(out.resolve("live-started.json"),json.writeValueAsString(Map.of("cases",8,"maximumInferenceRequests",50,"generation",generation.getGenerationKey(),"rerankCandidates",properties.getRerank().getMaxCandidates(),"batchSize",properties.getRerank().getBatchSize())));
        List<Object> rows=new ArrayList<>();int applied=0,disabledChecks=0;
        for(var c:cases){String key=c.path("key").asText();String query=c.path("question").asText();var constraints=c.has("rule")?json.treeToValue(c.path("rule"),RagBEvaluationService.EvaluationCase.class).constraints():RagBSearchConstraints.sources(Set.of());
            if(Set.of("fact-00-zh","github-langgraph").contains(key)){
                properties.getRerank().setEnabled(false);
                try{var current=search.retrieve(query,8,.55,constraints);List<Long> oldIds=new ArrayList<>();baselines.get(key).path("hits").forEach(h->oldIds.add(h.path("chunkId").asLong()));
                    assertEquals(oldIds,current.hits().stream().map(RagBSearchHit::chunkId).toList());assertEquals(baselines.get(key).path("answerPolicy").asText(),current.answerPolicy());assertEquals("DISABLED",current.rerank().status());disabledChecks++;
                }finally{properties.getRerank().setEnabled(true);}
            }
            var result=search.retrieve(query,8,.55,constraints);assertEquals(generation.getGenerationKey(),result.generation());
            if("APPLIED".equals(result.rerank().status())){applied++;assertTrue(result.hits().stream().allMatch(h->h.rerankScore()!=null));}
            assertTrue(result.hits().size()<=8);rows.add(Map.of("key",key,"result",result));
            Files.writeString(out.resolve("live-results.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(rows)+"\n");
            if(c.has("rule"))assertEquals(c.path("expectedPolicy").asText(),result.answerPolicy(),"Business policy regression: "+key);
        }
        assertTrue(applied>=6,"Real model must apply; fallback is not successful integration");assertEquals(2,disabledChecks);
        assertEquals(generation.getId(),mapper.selectActiveGeneration().getId());assertEquals(oldGeneration.path("generationChunkRows").asLong(),mapper.countGenerationChunks(generation.getId()));
        Files.writeString(out.resolve("live-completion.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("cases",8,"applied",applied,"disabledBaselineChecks",disabledChecks,"generationUnchanged",true,"corpusWrites",0,"chatCalls",0))+"\n");
    }
}
