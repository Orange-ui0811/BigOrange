package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.ragb.mapper.RagBMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicitly authorized, synthetic service-level comparison; no user auth, history or quota operations. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={
    "mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_TREND_AB_ACCEPTANCE",matches="true")
class TrendABProviderAcceptanceTest {
    @MockBean com.orangejd.server.customer.CustomerRuns disabledStartupRecovery;
    @MockBean PersonaPromptContextService disabledRealProfiles;
    @MockBean AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired TrendRagABPreviewService preview;
    @Autowired AiRuntimeConfigService runtime;
    @Autowired RagBMapper rag;
    @Autowired ObjectMapper json;

    @Test void compareAuthorizedThirtyTwoRuns() throws Exception {
        Path root=Files.isDirectory(Path.of("docs/rag-b"))?Path.of("."):Path.of("..");
        Path folder=root.resolve("docs/rag-b/trend-ab-20261009");Files.createDirectories(folder);
        Path raw=root.resolve("_experiment_staging/trend-ab-20261009");Files.createDirectories(raw);
        var cases=json.readTree(Files.readString(folder.resolve("cases.json"))).path("cases");
        assertThat(cases.size()).isEqualTo(8);
        var config=runtime.current(AiRuntimeConfigService.PROJECT);
        assertThat(config.endpoint()).isEqualTo("https://ai.gitee.com/v1");
        assertThat(config.model()).isEqualTo("DeepSeek-V4-Pro");
        var generation=rag.selectActiveGeneration();
        assertThat(generation.getEmbeddingProvider()).isEqualTo("OLLAMA");
        String index=generation.getGenerationKey();
        for(int round=1;round<=2;round++) for(int i=0;i<cases.size();i++) {
            var scenario=cases.get(i);String name=scenario.path("id").asText();
            var profile=json.treeToValue(scenario.path("profile"),TrendProfileDTO.class);
            assertThat(profile.getUsePersona()).isFalse();
            for(String side:(i+round)%2==0?List.of("B","A"):List.of("A","B")) {
                String key=name+"-"+round+"-"+side;
                Path record=folder.resolve(key+".json"), marker=folder.resolve(key+".started");
                if(Files.exists(record))continue;
                assertThat(Files.exists(marker)).as("Do not replay a request with unknown outcome: "+key).isFalse();
                assertThat(rag.selectActiveGeneration().getGenerationKey()).isEqualTo(index);
                var now=runtime.current(AiRuntimeConfigService.PROJECT);
                assertThat(now.version()).isEqualTo(config.version());
                assertThat(now.endpoint()).isEqualTo(config.endpoint());
                assertThat(now.model()).isEqualTo(config.model());
                Files.writeString(marker,Instant.now().toString());
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("case",name);row.put("round",round);row.put("variant",side);
                row.put("profile",profile);row.put("generation",index);row.put("startedAt",Instant.now().toString());
                long started=System.nanoTime();
                try {
                    var result=preview.preview(side,profile,true,true);
                    Object request=result.remove("modelRequest");
                    if(request!=null)Files.writeString(raw.resolve(key+"-request.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(request));
                    row.put("response",result);
                } catch(Exception failure) {
                    // Do not persist raw exception messages containing provider bodies.
                    row.put("error",failure.getClass().getSimpleName());
                }
                row.put("clientWallMs",(System.nanoTime()-started)/1_000_000.0);
                Files.writeString(record,json.writerWithDefaultPrettyPrinter().writeValueAsString(row));
                System.out.println("TREND_AB_COMPLETED "+key+" ms="+row.get("clientWallMs"));
            }
        }
        assertThat(rag.selectActiveGeneration().getGenerationKey()).isEqualTo(index);
    }
}
