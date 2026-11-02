package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSummaryServiceTest {
    final CustomerContextRepository repository=mock(CustomerContextRepository.class);
    final CustomerModel model=mock(CustomerModel.class);
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerSummaryService service=new CustomerSummaryService(repository,model,json,new CustomerProperties());
    final CustomerHistoryRecovery.Scope scope=new CustomerHistoryRecovery.Scope(1,2,"current","task-a",3,100);
    List<Map<String,Object>> originals(){
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=1;i<=30;i++)rows.add(Map.of("id",(long)i,"run_id","run-"+((i-1)/2),"role",i%2==1?"user":"assistant","content",(i%2==1?"用户要求必须离线，后来修改周期。":"客服尚未找到符合要求项目，未执行导入。").repeat(12),"task_id","task-a"));
        return rows;
    }
    @Test void summaryInputKeepsWholeTurnsExactIdsAndLeavesRecentThreeTurns(){
        var input=CustomerSummaryService.prepare(scope,originals(),json,4000);var messages=CustomerWorkflow.rows(input.get("messages"));
        assertThat(messages).isNotEmpty();assertThat(messages.size()%2).isZero();
        assertThat(messages.getLast().get("role")).isEqualTo("assistant");
        assertThat(messages).allMatch(m->((Number)m.get("messageId")).longValue()<=24);
        assertThat(CustomerModel.estimate(CustomerSummaryService.PROMPT+json.write(input))).isLessThanOrEqualTo(4000);
        assertThat(messages.getFirst().get("text")).isEqualTo(originals().getFirst().get("content"));
    }
    @Test void oversizeFirstTurnIsNotTruncatedOrSkippedWhileAdvancingCoverage(){
        var rows=originals();rows.set(0,new LinkedHashMap<>(rows.getFirst()));rows.getFirst().put("content","长原文".repeat(2000));
        assertThat(CustomerSummaryService.prepare(scope,rows,json,4000)).isEmpty();
    }
    @Test void repeatedAssistantStateDoesNotPreventUsefulBackgroundSummary(){
        var rows=originals();
        for(int i=1;i<rows.size();i+=2){var row=new LinkedHashMap<>(rows.get(i));row.put("content",json.write(Map.of("status","NO_MATCH","answer","仍未确认满足离线要求，尚未导入。","state",Map.of("audit","重复审计".repeat(3000)))));rows.set(i,row);}
        var input=CustomerSummaryService.prepare(scope,rows,json,4000);
        assertThat(input).isNotEmpty();assertThat(json.write(input)).contains("仍未确认满足离线要求，尚未导入。","ASSISTANT_RESPONSE_VIEW").doesNotContain("重复审计");
        assertThat(rows.get(1).get("content").toString()).contains("重复审计");
    }
    @Test void oversizedActualAssistantAnswerIsNeverTruncatedIntoFalseSummary(){
        var rows=originals();var row=new LinkedHashMap<>(rows.get(1));row.put("content",json.write(Map.of("answer","需要限定条件".repeat(2000))));rows.set(1,row);
        assertThat(CustomerSummaryService.prepare(scope,rows,json,4000)).isEmpty();
    }
    @Test void sourceListAndSummaryShapeAreStrict(){
        assertThat(CustomerSummaryService.validate(Map.of("summary","尚未导入","sourceMessageIds",List.of(1,2)),List.of(1L,2L))).isEqualTo("尚未导入");
        assertThatThrownBy(()->CustomerSummaryService.validate(Map.of("summary","伪造来源","sourceMessageIds",List.of(1,9)),List.of(1L,2L))).hasMessage("INVALID_CONTEXT_SUMMARY");
        assertThatThrownBy(()->CustomerSummaryService.validate(Map.of("summary","长".repeat(801),"sourceMessageIds",List.of(1,2)),List.of(1L,2L))).hasMessage("INVALID_CONTEXT_SUMMARY");
    }
    @Test void noRemainingTimeDoesNotAcquireLeaseOrCallModel(){
        assertThat(service.maybeSummarize(scope,Instant.now().plusSeconds(5),new ArrayList<>())).containsEntry("reason","INSUFFICIENT_TIME");verifyNoInteractions(repository,model);
    }
    @Test void modelFailurePreservesAnswerAndReleasesLeaseWithoutSaving(){
        when(repository.claimSummary(scope)).thenReturn("lease");when(repository.summaries(scope)).thenReturn(List.of());when(repository.completed(scope,0)).thenReturn(originals());
        when(model.call(eq(CustomerSummaryService.PROMPT),any(),any(),anyList())).thenThrow(new IllegalArgumentException("INVALID_MODEL_JSON"));
        assertThat(service.maybeSummarize(scope,Instant.now().plusSeconds(30),new ArrayList<>())).containsEntry("status","SKIPPED");
        verify(repository).releaseSummary(scope,"lease");verify(repository,never()).saveSummary(any(),anyString(),anyList(),anyString(),anyString(),anyString());
    }
    @Test void summaryUsesOriginalsAndStoresValidatedSegmentOnly(){
        when(repository.claimSummary(scope)).thenReturn("lease");when(repository.summaries(scope)).thenReturn(List.of());when(repository.completed(scope,0)).thenReturn(originals());
        when(model.call(eq(CustomerSummaryService.PROMPT),any(),any(),anyList())).thenAnswer(i->{
            var ids=CustomerWorkflow.rows(((Map<?,?>)i.getArgument(1)).get("messages")).stream().map(m->m.get("messageId")).toList();
            return Map.of("summary","用户有离线要求，客服未导入项目。","sourceMessageIds",ids);
        });
        when(repository.saveSummary(eq(scope),eq("lease"),anyList(),anyString(),anyString(),anyString())).thenReturn(true);
        assertThat(service.maybeSummarize(scope,Instant.now().plusSeconds(30),new ArrayList<>())).containsEntry("status","STORED");
    }
    @Test void changedSourceOrForeignTaskInvalidatesStoredSummary(){
        var row=new LinkedHashMap<String,Object>();row.put("id","summary");row.put("task_id","task-a");row.put("source_hash","old-hash");row.put("summary_text","历史背景");
        when(repository.summaries(scope)).thenReturn(List.of(row));when(repository.ids(row)).thenReturn(List.of(1L,2L));when(repository.sourceRows(scope,List.of(1L,2L))).thenReturn(originals().subList(0,2));
        assertThat(service.summaries(scope)).isEmpty();row.put("task_id","task-b");assertThat(service.summaries(scope)).isEmpty();
    }
    @Test void summaryPreparationDoesNotAttributeLegacyOrOtherTaskToCurrentTask(){
        var rows=originals().stream().map(r->{var copy=new LinkedHashMap<>(r);copy.remove("task_id");return (Map<String,Object>)copy;}).toList();
        assertThat(CustomerSummaryService.prepare(scope,rows,json,4000)).isEmpty();
    }
}
