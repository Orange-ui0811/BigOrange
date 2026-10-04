package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerReviewRecoveryTest {
    final CustomerStore store=mock(CustomerStore.class);
    final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerModel model=mock(CustomerModel.class);
    final CustomerTools tools=mock(CustomerTools.class);
    final CustomerTools.Context context=mock(CustomerTools.Context.class);
    final CustomerProjects projects=mock(CustomerProjects.class);
    final CustomerConversation conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
    Map<String,Object> state=new LinkedHashMap<>(Map.of("language","Python","goal","求职","topic",List.of("AI应用开发")));
    final List<Map<String,Object>> results=new ArrayList<>();
    CustomerReviewRecoveryTest() {
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","r");
        when(store.session(1,5)).thenAnswer(i->Map.of("state_json",json.write(state)));
        when(db.messages(1,Long.MAX_VALUE,40)).thenReturn(List.of());
        when(tools.context(eq("r"),eq(5L),any())).thenReturn(context);
        when(context.profile()).thenReturn("");
        doAnswer(i->{ results.add(i.getArgument(1));state=new LinkedHashMap<>(i.getArgument(2));return null; }).when(store).complete(eq("r"),anyMap(),anyMap(),any());
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        when(model.call(eq(CustomerPurpose.PLAN_PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("queries",List.of("AI application development")));
        when(context.projectRecall(anyString())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("test","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        when(context.purposeCandidates(anyMap(),anyList(),anyString())).thenReturn(List.of(new LinkedHashMap<>(Map.of("type","EXTERNAL_PROJECT","id",1,"name","demo/assistant","url","https://github.com/demo/assistant","description","构建可以检索知识并回答问题的人工智能应用。"))));
    }
    Map<String,Object> run(String message) {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));return results.getLast();
    }
    @Test void repeatedFailureThenExplanationNeverBecomesNoMatchOrChangesConditions() {
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList())).thenThrow(new AiToolProviderException(400,"HTTP_400",0,"private body must not be exposed",null));
        assertThat(run("帮我查找项目")).containsEntry("status","RELEVANCE_UNAVAILABLE");
        assertThat(run("开始查找吧")).containsEntry("status","RELEVANCE_UNAVAILABLE");
        assertThat(run("用途核对是什么")).containsEntry("status","PROCESS_EXPLANATION");
        assertThat(results.getLast().get("answer").toString()).contains("检查项目实际提供的功能","核对服务没有完成","不代表知识库没有").doesNotContain("没有找到符合","private body");
        assertThat(state).containsEntry("language","Python").containsEntry("goal","求职").containsEntry("topic",List.of("AI应用开发"));
        assertThat(CustomerPurpose.status(state)).isEqualTo("FAILED");
        verify(context,never()).diagnose(anyMap());
        verify(model,times(2)).call(eq(CustomerState.PROMPT),anyMap(),any(),anyList());
    }
    @Test void legacyFailureCannotPoisonDiagnosisEvenWhenActionModelMisroutes() {
        state.put(CustomerPurpose.STATE_KEY,Map.of("topic","AI应用开发","proofs",Map.of()));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","DIAGNOSE","changes",Map.of()));
        assertThat(run("能解释刚刚发生什么吗")).containsEntry("status","RELEVANCE_UNAVAILABLE");
        verify(context,never()).diagnose(anyMap());
        var diagnostic=CustomerDiagnostics.evaluate(projects,List.of(),state);
        assertThat(diagnostic).containsEntry("status","RELEVANCE_UNAVAILABLE").doesNotContainKeys("current","available");
    }
    @Test void successfulRetryReplacesFailureAndAllowsBoundedNoMatch() {
        CustomerPurpose.failed(state,new IllegalArgumentException("INVALID_PURPOSE_REVIEW"));
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("matches",List.of()));
        when(projects.select(anyList(),anyMap(),anyList())).thenReturn(List.of());
        when(context.diagnose(anyMap())).thenAnswer(i->CustomerDiagnostics.evaluate(projects,List.of(),i.getArgument(0)));
        var result=run("再查找一次");
        assertThat(result).containsEntry("status","NO_MATCH");
        assertThat(CustomerPurpose.status(state)).isEqualTo("SUCCEEDED_EMPTY");
        assertThat(result.get("answer").toString()).contains("本次已检查的候选","不代表整个知识库");
    }
    @Test void explanationModelCannotSmuggleConditionChanges() {
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","EXPLAIN","changes",Map.of("language",Map.of("value","Java","quote","Java"))));
        run("说明一下刚才的处理过程，Java 是另一个例子");
        assertThat(state.get("language")).isEqualTo("Python");
        verify(context,never()).diagnose(anyMap());verify(context,never()).projectRecall(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"用途核对是什么","什么是用途核对？","项目核对为什么失败了","为什么一直失败？","能解释一下相关性核对吗"})
    void operationalQuestionsHaveASeparateRoute(String text) {
        assertThat(CustomerConditionFacts.route(text)).isEqualTo("EXPLAIN");
    }
    @Test void projectRequestsAreNotSwallowedByExplanationRoute() {
        assertThat(CustomerConditionFacts.route("帮我找一个用途核对工具" )).isNull();
        assertThat(CustomerConditionFacts.route("当前有什么条件")).isEqualTo("STATE");
        assertThat(CustomerConditionFacts.route("怎么放宽条件才能推荐项目")).isEqualTo("DIAGNOSE");
    }
    @Test void contentRejectionSuppressesIdenticalReviewAndExplainsWithoutBlamingRequirements() {
        var http=org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.BAD_REQUEST,"bad request",org.springframework.http.HttpHeaders.EMPTY,"Content Exists Risk".getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList())).thenThrow(AiToolProviderException.from(http));
        run("帮我查找项目");run("开始查找吧");var reply=run("用途核对是什么");
        assertThat(reply.get("answer").toString()).contains("请求审核阶段拒绝","不是你需要放宽条件");
        verify(model,times(1)).call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList());
        assertThat(CustomerPurpose.contentRejected(state)).isTrue();
        verify(context,never()).diagnose(anyMap());
    }
    @Test void rejectionCircuitOnlyCoversSameInputWithinItsWindow() {
        var input=Map.<String,Object>of("topic","AI应用开发","originalRequest","AI应用开发","candidates",List.of(Map.of("key","EXTERNAL_PROJECT:1","purpose","测试用途说明")));
        var http=org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.BAD_REQUEST,"bad request",org.springframework.http.HttpHeaders.EMPTY,"Content Exists Risk".getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);
        CustomerPurpose.failed(state,AiToolProviderException.from(http),input);
        assertThat(CustomerPurpose.retryBlocked(state,input)).isTrue();
        var changed=new HashMap<>(input);changed.put("candidates",List.of());
        assertThat(CustomerPurpose.retryBlocked(state,changed)).isFalse();
        var review=new HashMap<String,Object>((Map<String,Object>)state.get(CustomerPurpose.STATE_KEY));
        review.put("failedAt",Instant.now().minusSeconds(301).toString());state.put(CustomerPurpose.STATE_KEY,review);state.put("purposeFailure",review);
        assertThat(CustomerPurpose.retryBlocked(state,input)).isFalse();
    }
    @Test void repeatedRecommendationReusesPlanAndPurposeButStillChecksCurrentProjects() {
        var card=new LinkedHashMap<String,Object>(Map.of("type","EXTERNAL_PROJECT","id",1,"name","demo/assistant","sourceRef","demo/assistant","url","https://github.com/demo/assistant","description","构建可以检索知识并回答问题的人工智能应用。"));
        when(context.purposeCandidates(anyMap(),anyList(),anyString())).thenReturn(List.of(card));
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("key","EXTERNAL_PROJECT:1","relation","DIRECT","quote",card.get("description")))));
        when(projects.select(anyList(),anyMap(),anyList())).thenReturn(List.of(card));
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("SYNTHETIC_EVIDENCE_OUTAGE"));
        run("帮我推荐项目");run("再推荐一次");
        verify(model,times(1)).call(eq(CustomerPurpose.PLAN_PROMPT),anyMap(),any(),anyList());
        verify(model,times(1)).call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList());
        verify(context,times(2)).projectRecall(anyString());verify(projects,times(2)).detail(eq("EXTERNAL_PROJECT"),eq(1L),anyMap());
        assertThat(CustomerTaskProgress.visible(state)).containsEntry("phase","ENRICHING").containsEntry("status","PROJECTS_WITH_LIMITATIONS");
        assertThat(card).containsEntry("importable",false);
    }
    @Test void newCandidateProviderFailureDoesNotDiscardPreviouslyVerifiedProject() {
        var card=new LinkedHashMap<String,Object>(Map.of("type","EXTERNAL_PROJECT","id",1,"name","demo/assistant","sourceRef","demo/assistant","url","https://github.com/demo/assistant","description","构建可以检索知识并回答问题的人工智能应用。"));
        var other=new LinkedHashMap<>(card);other.put("id",2);other.put("name","demo/other");other.put("url","https://github.com/demo/other");
        when(context.purposeCandidates(anyMap(),anyList(),anyString())).thenReturn(List.of(card)).thenReturn(List.of(card,other));
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList()))
            .thenReturn(Map.of("matches",List.of(Map.of("key","EXTERNAL_PROJECT:1","relation","DIRECT","quote",card.get("description")))))
            .thenThrow(new IllegalStateException("PROVIDER_UNAVAILABLE"));
        when(projects.select(anyList(),anyMap(),anyList())).thenAnswer(i->((List<Map<String,Object>>)i.getArgument(0)).stream().filter(p->CustomerPurpose.matches(p,i.getArgument(1))).toList());
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("SYNTHETIC_EVIDENCE_OUTAGE"));
        run("帮我推荐项目");var result=run("继续推荐");
        assertThat(result).containsEntry("purposeReviewIncomplete",true).containsEntry("status","PROJECTS_WITH_LIMITATIONS");
        assertThat((List<?>)result.get("projects")).hasSize(1);assertThat(CustomerPurpose.status(state)).isEqualTo("PARTIAL_MATCHES");
        assertThat(result.get("answer").toString()).contains("其他候选尚未完成核对");
    }
}
