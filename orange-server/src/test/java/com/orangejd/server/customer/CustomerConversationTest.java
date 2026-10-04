package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerConversationTest {
    CustomerStore store=mock(CustomerStore.class); CustomerMapper db=mock(CustomerMapper.class);
    CustomerJson json=new CustomerJson(new ObjectMapper()); CustomerModel model=mock(CustomerModel.class);
    CustomerTools tools=mock(CustomerTools.class); CustomerTools.Context context=mock(CustomerTools.Context.class);
    CustomerProjects projects=mock(CustomerProjects.class);
    CustomerConversation conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","r");
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","我想学习Java"));
        when(store.session(1,5)).thenReturn(Map.of("state_json","{}")); when(db.messages(1,Long.MAX_VALUE,40)).thenReturn(List.of());
        when(tools.context(eq("r"),eq(5L),any())).thenReturn(context); when(context.profile()).thenReturn("");
    }
    @Test void missingRequirementProducesOneQuestionAndPersistsState() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","COLLECT","changes",Map.of("goal",Map.of("value","学习","quote","学习"),"language",Map.of("value","Java","quote","Java"))));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","FOLLOW_UP"); assertThat(result.getValue().get("answer").toString()).contains("基础");
        verify(context,never()).knowledge(anyString(),anySet());
    }
    @Test void mixedExplanationAndChangePerformsExactlyOneRecommendationWithNewLimit() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","用途核对是什么？把周期放宽到半年，再推荐项目"));
        when(store.session(1,5)).thenReturn(Map.of("state_json",json.write(Map.of("maxDurationDays",60))));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","beforeTask",Map.of("intent","EXPLAIN","quote","用途核对是什么"),"changes",Map.of("maxDurationDays",Map.of("quote","周期放宽到半年","value",Map.of("quantity",6,"unit","MONTH","relation","AT_MOST")))));
        when(context.projects(anyMap())).thenReturn(List.of());
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(context,times(1)).projects(argThat(s->Integer.valueOf(180).equals(s.get("maxDurationDays"))));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class);verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue().get("answer").toString()).contains("180");
        assertThat(result.getValue().get("conditionChangeSummary").toString()).contains("60","180");
    }
    @Test void greetingUsesModelAndConfirmedProfileWithoutRetrievalAndPreservesConditions() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","你好！"));
        when(store.session(1,5)).thenReturn(Map.of("state_json","{\"language\":\"Python\"}"));
        when(context.profile()).thenReturn("已确认：学习Python");
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","你好！今天想聊什么？","changes",Map.of()));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class);
        var metrics=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),result.capture(),anyMap(),metrics.capture());
        assertThat(result.getValue()).containsEntry("status","CONVERSATION").containsEntry("profileLoaded",true);
        assertThat(result.getValue().get("answer").toString()).contains("你好");
        verify(model).call(eq(CustomerState.PROMPT),anyMap(),any(),anyList()); verify(context).profile(); verify(context,never()).knowledge(anyString(),anySet());
        assertThat(result.getValue().get("state")).isEqualTo(Map.of("language","Python"));
    }
    @Test void oldConversationGreetingKeepsReplyDespiteUnfoundedHistoricalConditions() {
        when(db.run("r")).thenReturn(Map.of("account_id",1,"session_id",1,"message","你好"));
        when(store.session(1,1)).thenReturn(Map.of("state_json","{\"language\":\"Java\",\"goal\":\"学习\",\"experience\":\"刚学\",\"allowDocker\":false}"));
        when(tools.context(eq("r"),eq(1L),any())).thenReturn(context);
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","COLLECT","reply","你好！继续聊聊吧。","changes",Map.of("language",Map.of("value","Python","quote","我刚学Python"),"allowDocker",Map.of("value",true,"quote","允许Docker"))));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","CONVERSATION").containsEntry("intent","CHAT").containsEntry("answer","你好！继续聊聊吧。");
        assertThat((Map<String,Object>)result.getValue().get("state")).containsEntry("language","Java").containsEntry("allowDocker",false);
        verify(context,never()).projects(anyMap()); verify(context,never()).knowledge(anyString(),anySet());
        verify(db).step(eq("r"),eq("conditionValidation"),eq("REJECTED_PROPOSALS"),anyString());
    }
    @Test void explicitRecommendationIsAllowedWithoutACompleteProfile() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        when(context.projects(anyMap())).thenReturn(List.of());
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(context).projects(anyMap());
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","NO_MATCH");
    }
    @Test void noMatchNeverRelaxesHardConditionsOrGeneratesProjects() {
        when(store.session(1,5)).thenReturn(Map.of("state_json","{\"goal\":\"学习\",\"experience\":\"新手\",\"language\":\"Java\",\"allowDocker\":false}"));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        when(context.projects(anyMap())).thenReturn(List.of()); conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","NO_MATCH").containsEntry("projects",List.of());
        verify(context,never()).knowledge(anyString(),anySet());
    }
    @Test void ungroundedConditionAsksForClarificationWithoutSearchingOrFailing() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of("language",Map.of("value","Python","quote","Java"))));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","NEEDS_CLARIFICATION");
        verify(context,never()).projects(anyMap()); verify(context,never()).knowledge(anyString(),anySet());
    }
    @Test void retrievalOutagePreservesRealCandidatesButNeverCreatesCitationsOrImportPermission() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        var card=new HashMap<String,Object>(Map.of("type","BEGINNER_PROJECT","id",7,"name","real","sourceRef","beginner:7"));
        when(context.projects(anyMap())).thenReturn(List.of(card));
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("B_INDEX_POLICY_MISMATCH"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","PROJECTS_WITH_LIMITATIONS");
        assertThat(card).containsEntry("importable",false).containsEntry("references",List.of());
        verify(projects).detail(eq("BEGINNER_PROJECT"),eq(7L),anyMap());
    }
    @Test void emptyKnowledgeProducesExplicitRefusalWithoutGeneration() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","QUESTION","changes",Map.of()));
        when(context.knowledge(anyString(),anySet())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("test","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("status","REFUSE_NO_MATCH").containsEntry("references",List.of());
        verify(model,times(1)).call(anyString(),any(),any(),anyList());
    }
    @Test void savedPreferencesAreIncludedInTheExactEvidencePayloadBudget() {
        String preference="希望保留面向小团队的实践方向。".repeat(10);
        when(store.session(1,5)).thenReturn(Map.of("state_json",json.write(Map.of("preferences",Collections.nCopies(6,preference)))));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        var card=new HashMap<String,Object>(Map.of("type","BEGINNER_PROJECT","id",7,"name","real","sourceRef","beginner:7"));
        when(context.projects(anyMap())).thenReturn(List.of(card));
        var hit=new com.orangejd.server.ragb.retrieval.RagBSearchHit(1,1,1,0,"real","BEGINNER_PROJECT","beginner:7","Intro",0,3000,"文档检索部署事实与实践示例。".repeat(200),.9,"HYBRID_RRF");
        when(context.projectEvidence(anyString(),anyList())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("g",0,1,Map.of(),List.of(hit)));
        when(model.call(eq(CustomerEvidence.PROMPT),anyMap(),any(),anyList())).thenAnswer(invocation->{
            Map<String,Object> payload=invocation.getArgument(1);
            assertThat(payload.get("question").toString()).contains(preference);
            assertThat(CustomerModel.estimate(CustomerEvidence.PROMPT+json.write(payload))).isLessThanOrEqualTo(6000);
            return Map.of("claims",List.of());
        });
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(model).call(eq(CustomerEvidence.PROMPT),anyMap(),any(),anyList());
    }
    @Test void restoredStringProjectIdResolvesToTheOriginalProject() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","第二个怎么部署"));
        when(store.session(1,5)).thenReturn(Map.of("state_json","{\"goal\":\"学习\",\"experience\":\"新手\",\"language\":\"Python\",\"lastCandidates\":[{\"type\":\"BEGINNER\",\"id\":\"6\",\"name\":\"first\"},{\"type\":\"BEGINNER\",\"id\":\"7\",\"name\":\"second\"}]}"));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(new HashMap<>(Map.of("intent","DETAIL","changes",Map.of())));
        var card=new HashMap<String,Object>(Map.of("type","BEGINNER","id","7","name","second","sourceRef","project:7"));
        when(context.detail(eq("BEGINNER"),eq(7L),anyMap())).thenReturn(card);
        when(context.projectEvidence(anyString(),anyList())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("test","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(context).detail(eq("BEGINNER"),eq(7L),anyMap());
        verify(projects).detail(eq("BEGINNER"),eq(7L),anyMap());
        verify(context,never()).detail(eq("BEGINNER"),eq(6L),anyMap());
        var finalState=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),anyMap(),finalState.capture(),any());
        assertThat((List<?>)finalState.getValue().get("lastCandidates")).hasSize(2);
        assertThat(((Map<?,?>)finalState.getValue().get("focusedProject")).get("id")).isEqualTo("7");
    }
    @Test void comparisonPreservesMultipleExplicitOrdinals() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","比较第一个和第三个"));
        when(store.session(1,5)).thenReturn(Map.of("state_json",json.write(Map.of("lastCandidates",List.of(
            Map.of("type","BEGINNER_PROJECT","id",1,"name","first"),Map.of("type","BEGINNER_PROJECT","id",2,"name","second"),Map.of("type","BEGINNER_PROJECT","id",3,"name","third"))))));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","COMPARE","positions",List.of(1,3),"changes",Map.of()));
        for(int position:List.of(1,3)) when(context.detail(eq("BEGINNER_PROJECT"),eq((long)position),anyMap())).thenReturn(new HashMap<>(Map.of("type","BEGINNER_PROJECT","id",position,"name","project"+position,"sourceRef","beginner:"+position)));
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("INDEX_UNAVAILABLE"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(context).detail(eq("BEGINNER_PROJECT"),eq(1L),anyMap());
        verify(context).detail(eq("BEGINNER_PROJECT"),eq(3L),anyMap());
        verify(context,never()).detail(eq("BEGINNER_PROJECT"),eq(2L),anyMap());
        var finalState=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),anyMap(),finalState.capture(),any());
        assertThat((List<?>)finalState.getValue().get("lastCandidates")).hasSize(3);
    }
    @Test void longHistoryKeepsConfirmedLanguageAndTraceableSummary() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","我刚才指定的语言是什么"));
        when(store.session(1,5)).thenReturn(Map.of("state_json","{\"goal\":\"学习\",\"experience\":\"新手\",\"language\":\"Python\"}"));
        List<Map<String,Object>> history=new ArrayList<>();
        for(int i=50;i>10;i--) history.add(Map.of("id",i,"run_id","old","role","user","content","其他话题".repeat(30)));
        when(db.messages(1,Long.MAX_VALUE,40)).thenReturn(history);
        when(db.messages(1,11,100)).thenReturn(List.of(Map.of("id",10,"role","user","content","之前选择Python")));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","QUESTION","changes",Map.of()));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class); verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue().get("answer").toString()).contains("Python"); verify(db).summary(eq(1L),contains("messageId"),eq(16L));
        verify(context,never()).knowledge(anyString(),anySet());
    }
}
