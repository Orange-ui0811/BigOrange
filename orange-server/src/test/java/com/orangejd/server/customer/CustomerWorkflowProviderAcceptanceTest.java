package com.orangejd.server.customer;

import com.orangejd.server.service.PersonaPromptContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local opt-in: real graph, database, current B index and configured model. Synthetic chat data only. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"orange.customer.enabled=true","orange.customer.execution-engine=LANGGRAPH4J","orange.customer.all-authenticated-users=true","mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_WORKFLOW_V2_ACCEPTANCE",matches="true")
class CustomerWorkflowProviderAcceptanceTest {
    @MockBean CustomerRuns disabledStartupRecovery;
    @MockBean PersonaPromptContextService profiles;
    @MockBean com.orangejd.server.service.AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor") org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired CustomerStore store;@Autowired CustomerMapper db;@Autowired CustomerJson json;@Autowired CustomerConversation conversation;
    @Autowired LangGraphCustomerEngine graph;@Autowired CustomerExecutionTrace trace;
    List<Map<String,Object>> records=new ArrayList<>();Path report;
    @Test void jobAndShopUseRealProvidersWithinPersistentFiftyRequestBudget() throws Exception {
        assertThat(System.getenv("CUSTOMER_ACCEPTANCE_LEDGER")).as("Persistent HTTP budget is mandatory").isNotBlank();
        when(profiles.confirmedContext(anyLong())).thenReturn("");
        report=Path.of(System.getenv().getOrDefault("WORKFLOW_V2_REPORT","../docs/customer/workflow-v2/provider-result.json"));Files.createDirectories(report.toAbsolutePath().getParent());
        long job=CustomerStore.number(store.create(900921L,"Workflow v2 Java acceptance"),"id");
        long shop=CustomerStore.number(store.create(900922L,"Workflow v2 shop acceptance"),"id");
        try {
            var first=turn(job,900921,"我想做Java求职项目，先帮我确认一个最关键的问题。",Map.of());
            String question=Objects.toString(CustomerWorkflow.question(first.state()).get("id"),"");
            var interruption=turn(job,900921,"先插问一下，Redis是什么？",Map.of());
            if(!question.isBlank())assertThat(CustomerWorkflow.question(interruption.state()).get("id")).isEqualTo(question);
            var answer=turn(job,900921,"Java后端实习，刚学过Spring Boot，希望两周内完成；先记录，不用推荐。",Map.of());
            assertThat(CustomerRequirementService.query(answer.state())).containsAnyOf("Java","后端","实习");
            var recommended=turn(job,900921,"先看看",Map.of());
            assertThat(recommended.result().get("workflowVersion")).isEqualTo(2);
            var shopNeeds=turn(shop,900922,"给小店找库存管理系统，必须支持扫码，日常必须离线运行，可以接受少量改造。先记录，不用推荐。",Map.of());
            assertThat(CustomerRequirementService.query(shopNeeds.state())).contains("离线");
            var shopResults=turn(shop,900922,"先看看",Map.of());
            assertThat(CustomerWorkflow.rows(shopResults.result().get("projects"))).allMatch(p->p.get("requirementMatches") instanceof List<?>);
            var strict=turn(shop,900922,"只要已核实满足全部必要条件的项目",Map.of());
            for(var card:CustomerWorkflow.rows(strict.result().get("projects")))assertThat(CustomerWorkflow.rows(card.get("requirementMatches"))).noneMatch(m->"HARD".equals(m.get("strength"))&&!"SATISFIED".equals(m.get("verdict")));
        } finally {writeReport();}
    }
    CustomerExecutionResult turn(long session,long owner,String message,Map<String,Object> binding) throws Exception {
        var run=store.submit(session,owner,UUID.randomUUID().toString(),message,binding);String id=run.get("id").toString();
        var ledger=new CustomerUsageLedger(id,trace);long start=System.nanoTime();var record=new LinkedHashMap<String,Object>();record.put("runId",id);record.put("sessionId",session);record.put("message",message);
        try {
            var result=graph.execute(new CustomerRunContext(id,Instant.now().plusSeconds(90),ledger));store.complete(id,result.result(),result.state(),conversation.metrics(ledger));
            record.put("status","SUCCEEDED");record.put("result",result.result());return result;
        } catch(RuntimeException ex) {
            String code=CustomerRecovery.code(ex);record.put("status","FAILED");record.put("errorCode",code);store.terminal(id,"FAILED",code,conversation.metrics(ledger));throw ex;
        } finally {record.put("durationMs",(System.nanoTime()-start)/1_000_000);record.put("usage",conversation.metrics(ledger));records.add(record);writeReport();}
    }
    void writeReport() throws Exception {Files.writeString(report,json.write(Map.of("scope","Real local graph + B retrieval + configured providers; synthetic owners; HTTP/UI authentication is a separate gate","rows",records,"releaseApproved",false)));}
}
