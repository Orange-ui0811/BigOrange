package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Explicitly opted-in local corpus rebuild. No chat model or external embedding requests. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={"mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_RAG_B_LOCAL_ACCEPTANCE", matches="true")
class RagBLocalModelAcceptanceTest {
    @MockBean com.orangejd.server.customer.CustomerRuns disabledStartupRecovery;
    @MockBean com.orangejd.server.service.AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired RagBProperties properties;
    @Autowired RagBMapper mapper;
    @Autowired RagBGenerationService generations;
    @Autowired RagBSearchService search;

    @Test void rebuildsSeparatePinnedIndexThenTestsRealRetrieval() throws Exception {
        assertThat(properties.getEmbeddingProvider()).isEqualTo("OLLAMA");
        assertThat(properties.getEmbeddingRevision()).startsWith("sha256:");
        Path root=Files.isDirectory(Path.of("docs/rag-b"))?Path.of("."):Path.of("..");
        Path folder=root.resolve(System.getProperty("local.m3.report-dir", "docs/rag-b/local-m3-20261008")).normalize();Files.createDirectories(folder);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var old=mapper.selectActiveGeneration();
        if(Boolean.getBoolean("local.m3.verify-only")) {
            assertThat(old.getEmbeddingProvider()).as("Verification must not create or switch an index").isEqualTo("OLLAMA");
            assertThat(old.getEmbeddingRevision()).isEqualTo(properties.getEmbeddingRevision());
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("previousGeneration",old.getGenerationKey());
        report.put("previousDocuments",old.getDocumentCount());report.put("previousChunks",old.getChunkCount());
        report.put("previousIndexedChunkRows",mapper.countGenerationChunks(old.getId()));
        report.put("scope","Local model and B corpus only; no generation/LLM quality claims.");
        var generation=old;
        long started=System.nanoTime();
        if(!"OLLAMA".equals(old.getEmbeddingProvider()) || !properties.getEmbeddingRevision().equals(old.getEmbeddingRevision())) {
            generation=new RagBIndexGeneration();
            String key="local-m3-"+System.currentTimeMillis();generation.setGenerationKey(key);
            generation.setRedisPrefix(properties.getRedisPrefix()+"g:"+key+":");
            generation.setAdapterType("HNSW_HYBRID");generation.setEmbeddingModel("bge-m3");
            generation.setEmbeddingDimensions(1024);generation.setEmbeddingProvider("OLLAMA");
            generation.setEmbeddingRevision(properties.getEmbeddingRevision());generation.setChunkPolicy(properties.getChunkPolicy());
            generation.setStatus("BUILDING");mapper.insertGeneration(generation);
            report.put("newGenerationId",generation.getId());
            Files.writeString(folder.resolve("integration-progress.json"),json.writeValueAsString(report));
            report.put("rebuild",generations.rebuild(generation.getId()));
        }
        report.put("rebuildMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        report.put("activeIndex",generations.activeConfiguration());
        assertThat(mapper.selectActiveGeneration().getId()).isEqualTo(generation.getId());
        var rows=new CopyOnWriteArrayList<Map<String,Object>>();
        var pool=Executors.newFixedThreadPool(2);
        var queries=List.of("Python RAG 知识库问答项目", "Java Spring Boot 项目", "AI 应用开发 智能体",
                "Vue 前端项目", "Docker 部署", "项目安装运行方法", "聊天机器人", "数据库依赖");
        try {
            for(int batch=0;batch<queries.size();batch+=2) {
                var work=new ArrayList<Callable<Void>>();
                for(String query:queries.subList(batch,batch+2)) work.add(()->{
                    long start=System.nanoTime();var result=search.retrieve(query,8,0.55,Set.of());
                    rows.add(Map.of("query",query,"durationMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),
                            "status",result.status(),"hitCount",result.hits().size(),"generation",result.generation(),
                            "documentIds",result.hits().stream().map(h->h.documentId()).distinct().toList()));return null;
                });
                for(var future:pool.invokeAll(work)) future.get();
            }
            // A name query must resolve a real source and retain offsets, independently of generic semantic quality.
            var source=mapper.selectReadyDocuments().stream().filter(d->d.getTitle()!=null && d.getTitle().contains("/")).findFirst().orElseThrow();
            var named=search.retrieve(source.getTitle(),8,0.55,Set.of());
            assertThat(named.hits()).anyMatch(h->h.documentId()==source.getId());
            assertThat(rows).anyMatch(r->((Number)r.get("hitCount")).intValue()>0);
            report.put("retrieval",rows);report.put("namedSourceId",source.getId());report.put("namedSourceHits",named.hits().size());
            report.put("success",true);
        } finally {
            pool.shutdownNow();Files.writeString(folder.resolve("integration.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}
