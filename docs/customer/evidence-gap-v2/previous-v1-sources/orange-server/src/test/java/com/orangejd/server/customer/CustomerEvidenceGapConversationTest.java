package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import com.orangejd.server.ragb.service.RagBSearchService;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Calls the real turn router, citation restoration, verifier boundary and persisted response. */
class CustomerEvidenceGapConversationTest {
    CustomerStore store;CustomerMapper db;CustomerModel model;CustomerTools tools;CustomerTools.Context context;CustomerProjects projects;
    CustomerJson json=new CustomerJson(new ObjectMapper());CustomerConversation conversation;
    @BeforeEach void setup(){
        store=mock(CustomerStore.class);db=mock(CustomerMapper.class);model=mock(CustomerModel.class);tools=mock(CustomerTools.class);context=mock(CustomerTools.Context.class);projects=mock(CustomerProjects.class);
        conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","r");
        when(store.session(1,5)).thenReturn(CustomerWorkflowFixtures.session("{}"));when(db.messages(1,Long.MAX_VALUE,40)).thenReturn(List.of());
        when(tools.context(eq("r"),eq(5L),any())).thenReturn(context);when(context.profile()).thenReturn("");
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","QUESTION","changes",Map.of()));
    }
    Map<String,Object> run(String message){
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var captor=org.mockito.ArgumentCaptor.forClass(Map.class);verify(store).complete(eq("r"),captor.capture(),anyMap(),any());
        var result=captor.getValue();String output=System.getProperty("customer.gaps.output");
        if(output!=null)try{
            var folder=java.nio.file.Path.of(output);java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve(getClass().getSimpleName()+"-"+CustomerStore.hash(message).substring(0,16)+".json"),json.write(Map.of("message",message,"result",result))+"\n");
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        return result;
    }
    void evidence(String text,String policy,Map<String,Object> verdict){
        var h=new RagBSearchHit(1,1,1,0,"Demo","GITHUB","owner/demo","Requirements",0,text.length(),text,.9,"HYBRID_RRF");
        when(context.knowledge(anyString(),anySet())).thenReturn(new RagBSearchService.SearchResult("g","HNSW_HYBRID",1,1,Map.of(),List.of(h),List.of(),policy));
        when(model.call(eq(CustomerEvidence.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","field","prerequisites","text","文档说明："+text))));
        when(model.call(eq(CustomerEvidence.VERIFY_PROMPT),anyMap(),any(),anyList())).thenReturn(verdict);
    }
    @Test void shortVersionFactIsVerifiedInsteadOfAskingForTheUsersProfile(){
        evidence("Requires Go 1.22+.","REFUSE_INSUFFICIENT_EVIDENCE",Map.of("supportedIds",List.of(0),"coverage","COMPLETE","missingFacts",List.of()));
        var r=run("根据文档，Demo 构建要求哪个Go版本？");
        assertThat(r).containsEntry("status","SUPPORTED_EXTRACTS").doesNotContainKeys("pendingQuestion","question");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","SUPPORTED");
        assertThat(CustomerWorkflow.rows(r.get("references"))).hasSize(1);
        verify(model).call(eq(CustomerEvidence.VERIFY_PROMPT),argThat(payload->payload instanceof Map<?,?> m&&m.get("question").toString().contains("Go版本")),any(),anyList());
    }
    @Test void shortLicenseCanUseAnExactServerSpanWithoutRelaxingRawQuoteValidation(){
        evidence("MIT","REFUSE_INSUFFICIENT_EVIDENCE",Map.of("supportedIds",List.of(0),"coverage","COMPLETE","missingFacts",List.of()));
        var r=run("根据知识库文档，Demo是什么许可证？");assertThat(r).containsEntry("status","SUPPORTED_EXTRACTS");
        assertThat(CustomerWorkflow.rows(r.get("references")).getFirst()).containsEntry("quote","MIT");
    }
    @Test void heapTroubleshootingDoesNotAnswerMinimumMachineMemory(){
        evidence("If build fails, set NODE_OPTIONS=--max-old-space-size=4096.","ANSWER_WITH_CITATIONS",Map.of("supportedIds",List.of(0),"coverage","INSUFFICIENT", "missingFacts",List.of(Map.of("questionQuote","最低机器内存","reason","堆排障参数不等于最低机器要求"))));
        var r=run("根据项目文档，Demo的最低机器内存是多少？");
        assertThat(r).containsEntry("status","REFUSE_INSUFFICIENT_EVIDENCE").containsEntry("references",List.of()).doesNotContainKeys("pendingQuestion","question");
        assertThat(r.get("answer").toString()).contains("最低机器内存","待核实").doesNotContain("4096");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
    }
    @Test void partialAnswerKeepsKnownFactsAndNamesTheUnverifiedPart(){
        evidence("Install Demo with npm install demo and start it using npx demo start.","ANSWER_WITH_CITATIONS",Map.of("supportedIds",List.of(0),"coverage","PARTIAL", "missingFacts",List.of(Map.of("questionQuote","最低内存","reason","文档只说明了命令"))));
        var r=run("根据文档，Demo的安装命令和最低内存分别是什么？");
        assertThat(r).containsEntry("status","PARTIALLY_SUPPORTED_EXTRACTS").containsEntry("businessOutcome","PARTIAL_ANSWER");
        assertThat(r.get("answer").toString()).contains("npm install demo","最低内存","待核实");
        assertThat(r).doesNotContainKeys("pendingQuestion","question");
    }
    @Test void factQaDoesNotReviveAnUnrelatedOpenRecommendationQuestion(){
        var state=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(state,List.of(),"before");
        CustomerTaskService.ask(state,"REQUIREMENT","你有多少时间做项目？",List.of(),"time","old");
        when(store.session(1,5)).thenReturn(CustomerWorkflowFixtures.session(json.write(state)));
        evidence("Requires Go 1.22+.","REFUSE_INSUFFICIENT_EVIDENCE",Map.of("supportedIds",List.of(0),"coverage","COMPLETE","missingFacts",List.of()));
        var r=run("根据知识库文档，Demo要求哪个Go版本？");
        assertThat(r).doesNotContainKeys("pendingQuestion","question");
        assertThat(CustomerWorkflow.question(CustomerWorkflow.map(r.get("state")))).containsEntry("status","OPEN").containsEntry("text","你有多少时间做项目？");
    }
    @Test void missingEvidenceNeverAsksUserToVerifyProjectCapabilities(){
        when(context.knowledge(anyString(),anySet())).thenReturn(new RagBSearchService.SearchResult("g","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        var r=run("根据项目文档，免费社区版是否自带单点登录？");
        assertThat(r).containsEntry("status","REFUSE_NO_MATCH").doesNotContainKeys("pendingQuestion","question");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
        verify(model,times(1)).call(anyString(),any(),any(),anyList());
    }
    @Test void serviceFailureKeepsKnownConditionsAndDoesNotAskForLooserRequirements(){
        when(context.knowledge(anyString(),anySet())).thenThrow(new IllegalStateException("EMBEDDING_COOLDOWN"));
        var r=run("根据项目文档说明部署要求");
        assertThat(r).containsEntry("status","KNOWLEDGE_UNAVAILABLE").doesNotContainKeys("pendingQuestion","question");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","VERIFICATION_UNAVAILABLE").containsEntry("nextAction","RETRY_VERIFICATION");
    }
    @Test void genuineUsageAmbiguityUsesTheTaskQuestionInsteadOfInventingProjectFacts(){
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","COLLECT","changes",Map.of(),
                "clarification",Map.of("question","你准备直接部署使用，还是二次开发？","options",List.of("直接部署","二次开发"))));
        var r=run("想找知识库项目，但还没确定怎么用");
        assertThat(r).containsEntry("status","FOLLOW_UP").containsEntry("pendingQuestion","你准备直接部署使用，还是二次开发？");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","USER_INPUT_REQUIRED");
        verify(context,never()).knowledge(anyString(),anySet());
    }
    @Test void legacyVerifierWithoutCoverageDoesNotPretendToHaveAnsweredEverything(){
        evidence("Requires Go 1.22+.","REFUSE_INSUFFICIENT_EVIDENCE",Map.of("supportedIds",List.of(0)));
        var r=run("根据文档，Demo要求哪个Go版本？");
        assertThat(r).containsEntry("status","PARTIALLY_SUPPORTED_EXTRACTS");
        assertThat(CustomerWorkflow.map(r.get("questionCoverage"))).containsEntry("status","UNASSESSED");
    }
    @Test void personalDeploymentRequestCanAskForUserEnvironmentWithoutAssumingProjectRequirements(){
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","COLLECT","changes",Map.of(),
                "clarification",Map.of("question","你准备使用什么系统、可用内存大约多少？")));
        var r=run("帮我推荐能部署到我电脑上的知识库项目");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","USER_INPUT_REQUIRED");
        assertThat(r.get("pendingQuestion").toString()).contains("系统","内存");
        assertThat(CustomerWorkflow.rows(CustomerWorkflow.map(r.get("evidenceDecision")).get("projectFacts"))).isEmpty();
    }
}
