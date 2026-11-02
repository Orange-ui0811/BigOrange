package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mock service boundaries only: no database, provider requests, or live acceptance ledger. */
@ResourceLock("acceptance-ledger")
class CustomerContextSupportTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerProperties config=new CustomerProperties();
    final CustomerHistoryRecovery recovery=mock(CustomerHistoryRecovery.class);
    final CustomerSummaryService summaries=mock(CustomerSummaryService.class);
    final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerHistoryRecovery.Session reader=mock(CustomerHistoryRecovery.Session.class);
    final ToolCallback search=mock(ToolCallback.class),read=mock(ToolCallback.class);
    final CustomerTools.Context tools=mock(CustomerTools.Context.class);
    final CustomerConversation conversation=new CustomerConversation(mock(CustomerStore.class),db,json,mock(CustomerModel.class),mock(CustomerTools.class),mock(CustomerProjects.class),config);
    final CustomerContextSupport support=new CustomerContextSupport(recovery,summaries,db,json,config);
    @TempDir Path temp;

    CustomerContextSupportTest(){
        when(recovery.open(any())).thenReturn(reader);
        when(recovery.recent(any(),eq(80))).thenReturn(List.of());
        when(summaries.summaries(any())).thenReturn(List.of());
        when(reader.callbacks()).thenReturn(new ToolCallback[]{search,read});
        when(reader.callbacks(anyBoolean())).thenReturn(new ToolCallback[]{search,read});
        when(search.call(anyString())).thenReturn("{\"sources\":[]}");
        when(tools.contextRead(anyString(),anyMap(),any())).thenAnswer(call->((java.util.function.Supplier<String>)call.getArgument(2)).get());
    }
    CustomerConversation.Turn turn(int seconds){
        var t=conversation.newTurn(new CustomerRunContext("r",Instant.now().plusSeconds(seconds),new ArrayList<>()));
        t.owner=5;t.sessionId=1;t.messageId=100L;t.message="你好";t.profile="";t.context=tools;
        t.prior=new LinkedHashMap<>();CustomerWorkflow.initialize(t.prior,List.of(),"initial");t.state=t.prior;
        t.history=new CustomerHistory.View(List.of(),"",0);t.resolution=CustomerReferenceResolver.resolve(t.message,t.prior,Map.of());return t;
    }
    Map<String,Object> source(CustomerConversation.Turn t,boolean partial,String text){
        return Map.of("messageId",3L,"taskId",CustomerWorkflow.task(t.prior).get("id"),"text",text,"contentHash","source-hash","start",0,"end",text.length(),"partial",partial);
    }
    @Test void prepareRequestsBoundedRecentWindowAtFrozenMessageWatermark(){
        var t=turn(90);
        when(recovery.recent(any(),eq(80))).thenReturn(List.of(
            Map.of("id",1L,"run_id","old","task_id",CustomerWorkflow.task(t.prior).get("id"),"role","user","content","之前讨论的普通背景"),
            Map.of("id",2L,"run_id","old","task_id",CustomerWorkflow.task(t.prior).get("id"),"role","assistant","content",json.write(Map.of("answer","已经说明","status","CONVERSATION")))));
        support.prepare(t);var captured=ArgumentCaptor.forClass(CustomerHistoryRecovery.Scope.class);
        verify(recovery).recent(captured.capture(),eq(80));
        assertThat(captured.getValue()).isEqualTo(new CustomerHistoryRecovery.Scope(5,1,"r",CustomerWorkflow.task(t.prior).get("id").toString(),0,100));
        assertThat(t.memory.scope.watermark()).isEqualTo(100);assertThat(t.memory.recent).hasSize(2);
        assertThat(t.memory.recent.getFirst()).containsEntry("messageId",1L).containsEntry("sessionId",1L);
        verify(recovery).open(captured.getValue());
    }
    @Test void missingDatabaseMessageIdentityNeverManufacturesArchiveScope(){
        var t=turn(90);t.messageId="run:r";support.prepare(t);assertThat(t.memory).isNull();verifyNoInteractions(recovery,summaries);
    }
    @Test void ordinaryShortTurnDoesNotTriggerAnyHistoryCallback(){
        var t=turn(90);support.prepare(t);support.recover(t);
        assertThat(t.memory.attempted).isFalse();verifyNoInteractions(search,read);verify(reader,never()).callbacks();verify(reader,never()).callbacks(anyBoolean());
    }
    @Test void explicitRecoveryUsesSearchAndAtMostOneSourceReadThenStops(){
        var t=turn(90);t.message="还记得之前那个项目吗";support.prepare(t);
        when(search.call(anyString())).thenReturn(json.write(Map.of("sources",List.of(source(t,true,"四周是后来修改的周期")))));
        when(read.call(anyString())).thenReturn(json.write(source(t,false,"先别导入，四周是后来修改的周期。")));
        when(reader.calls()).thenReturn(2);
        support.recover(t);support.recover(t);
        assertThat(t.memory.attempted).isTrue();assertThat(t.memory.sources).hasSize(1);
        assertThat(t.memory.sources.getFirst()).containsEntry("partial",false).containsEntry("text","先别导入，四周是后来修改的周期。");
        verify(search,times(1)).call(anyString());
        verify(reader,times(1)).callbacks(false);
        var args=ArgumentCaptor.forClass(String.class);verify(read,times(1)).call(args.capture());
        assertThat(json.read(args.getValue())).containsEntry("start",0).containsEntry("length",1200).containsEntry("contentHash","source-hash");
    }
    @Test void modelRequestedRecoveryAllowsOnlyOneReevaluationAndBoundsQuery(){
        var t=turn(90);support.prepare(t);var action=Map.<String,Object>of("needsHistory",true,"historyQuery","早期目标与更正".repeat(80));
        assertThat(support.recoverRequested(t,action)).isTrue();assertThat(support.recoverRequested(t,action)).isFalse();
        var args=ArgumentCaptor.forClass(String.class);verify(search,times(1)).call(args.capture());
        assertThat(json.read(args.getValue()).get("query").toString()).hasSize(300);verifyNoInteractions(read);
    }
    @Test void modelRequestedRecoveryIsSkippedWhenRemainingTimeIsInsufficient(){
        var t=turn(10);support.prepare(t);assertThat(support.recoverRequested(t,Map.of("needsHistory",true))).isFalse();
        verifyNoInteractions(search,read);assertThat(t.memory.attempted).isFalse();
    }
    @Test void onlyExplicitUserResumeCanSearchOtherTaskSources(){
        var t=turn(90);t.message="恢复之前那个任务，要求不变";support.prepare(t);
        var other=new LinkedHashMap<>(source(t,false,"上一个任务选了甲项目"));other.put("taskId","earlier-task");
        when(search.call(anyString())).thenReturn(json.write(Map.of("sources",List.of(other))));
        support.recover(t);var input=support.input(t);
        verify(reader,times(1)).callbacks(true);
        assertThat(CustomerWorkflow.rows(input.get("historicalSources")).getFirst()).containsEntry("historicalOnly",true);
        assertThat(CustomerWorkflow.map(CustomerWorkflow.map(input.get("state")).get("workflowTask")).get("id")).isEqualTo(CustomerWorkflow.task(t.prior).get("id"));
    }
    @Test void modelQueryCannotExpandTaskScopeWithoutUserResumeWording(){
        var t=turn(90);support.prepare(t);
        assertThat(support.recoverRequested(t,Map.of("needsHistory",true,"historyQuery","恢复之前那个任务"))).isTrue();
        verify(reader,times(1)).callbacks(false);verify(reader,never()).callbacks(true);
    }
    @Test void metadataRecordsIdentifiersButNeverRecoveredTextOrCurrentMessage(){
        var t=turn(90);t.message="还记得之前谈到的秘密项目名吗";support.prepare(t);
        when(search.call(anyString())).thenReturn(json.write(Map.of("sources",List.of(source(t,false,"私密原话：先别导入项目。")))));
        support.recover(t);var input=support.input(t);
        assertThat(json.write(input)).contains("私密原话：先别导入项目。");
        var details=ArgumentCaptor.forClass(String.class);verify(db,atLeast(3)).step(eq("r"),anyString(),eq("RECORDED"),details.capture());
        assertThat(details.getAllValues()).allMatch(value->!value.contains("私密原话")&&!value.contains("秘密项目名"));
        assertThat(details.getAllValues()).anyMatch(value->value.contains("sourceRefs")&&value.contains("message:3"));
    }
    @Test void summaryMaintenanceSkipsWhenTurnHasInsufficientTime(){
        var t=turn(5);support.prepare(t);support.archive(t);verify(summaries,never()).maybeSummarize(any(),any(),anyList());
        verify(db).step(eq("r"),eq("contextSummary"),eq("RECORDED"),contains("SKIPPED_BUDGET"));
    }
    @Test void summaryMaintenanceChecksCapacityWithoutChangingExhaustedLedger() throws Exception {
        var ledger=temp.resolve("exhausted.jsonl");Files.writeString(ledger,"{\"attempt\":1}\n");
        String before=Files.readString(ledger),oldFile=System.getProperty("orange.acceptance.ledger"),oldLimit=System.getProperty("orange.acceptance.limit");
        try{
            System.setProperty("orange.acceptance.ledger",ledger.toString());System.setProperty("orange.acceptance.limit","1");
            var t=turn(90);support.prepare(t);support.archive(t);
            verify(summaries,never()).maybeSummarize(any(),any(),anyList());assertThat(Files.readString(ledger)).isEqualTo(before);
        }finally{
            if(oldFile==null)System.clearProperty("orange.acceptance.ledger");else System.setProperty("orange.acceptance.ledger",oldFile);
            if(oldLimit==null)System.clearProperty("orange.acceptance.limit");else System.setProperty("orange.acceptance.limit",oldLimit);
        }
    }
    @Test void summaryModelHasExplicitPhaseBudgetAndNeverInheritsGlobalThinking(){
        var client=mock(ExternalAiClient.class);config.setThinking(true);
        when(client.callCustomer(anyString(),anyString(),anyInt(),eq(1000),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"summary\":\"背景\",\"sourceMessageIds\":[1,2]}","fixture",Map.of(),1));
        var model=new CustomerModel(client,config,json);var usage=new ArrayList<Map<String,Object>>();
        model.call(CustomerSummaryService.PROMPT,Map.of("messages",List.of()),Instant.now().plusSeconds(90),usage);
        assertThat(usage).hasSize(1);assertThat(usage.getFirst()).containsEntry("phase","SUMMARY").containsEntry("outputBudget",1000).containsEntry("thinking",false);
        assertThat((Integer)usage.getFirst().get("timeoutSeconds")).isLessThanOrEqualTo(8);
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),eq(1000),eq(false));
    }
    @Test void summaryModelInputBudgetBlocksBeforeTransport(){
        var client=mock(ExternalAiClient.class);var model=new CustomerModel(client,config,json);
        assertThatThrownBy(()->model.call(CustomerSummaryService.PROMPT,Map.of("messages","历史".repeat(2100)),Instant.now().plusSeconds(90),new ArrayList<>())).hasMessage("CONTEXT_BUDGET_EXCEEDED");
        verifyNoInteractions(client);
    }
}
