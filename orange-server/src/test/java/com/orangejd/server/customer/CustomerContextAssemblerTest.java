package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerContextAssemblerTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    Map<String,Object> state(){
        var state=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(state,List.of(),"initial");
        var task=CustomerWorkflow.task(state);
        var req=CustomerRequirementService.entry(task,"open","必须离线，周期改为四周","必须离线，周期改为四周","HARD",1L,"必须离线，周期改为四周","MESSAGE");
        task.put("requirements",List.of(req));task.put("requirementVersion",3L);return state;
    }
    CustomerContextPacket packet(Map<String,Object> state,List<Map<String,Object>> history,List<Map<String,Object>> sources,List<Map<String,Object>> summaries,int budget){
        return CustomerContextAssembler.understanding("继续之前那个，其他要求不变",state,"",history,"",budget,json,Map.of(),CustomerContextPacket.Scope.from(1,state,200),sources,summaries);
    }
    Map<String,Object> source(Map<String,Object> state,long id,String text){return Map.of("messageId",id,"taskId",CustomerWorkflow.task(state).get("id"),"text",text,"start",0,"end",text.length(),"contentHash","hash-"+id,"partial",false);}
    Map<String,Object> summary(Map<String,Object> state,String id,List<Long> ids,String text){return Map.of("id",id,"taskId",CustomerWorkflow.task(state).get("id"),"sourceMessageIds",ids,"summary",text,"sourceHash","source-"+id);}
    @Test void sixtyTurnsRemainBoundedWithoutLosingCurrentCorrectionOrNegation(){
        var state=state();var history=new ArrayList<Map<String,Object>>();
        for(int i=0;i<60;i++){
            history.add(Map.of("id",i*2+1L,"role","user","content","讨论普通技术背景".repeat(15)));
            history.add(Map.of("id",i*2+2L,"role","assistant","content","说明相关概念".repeat(15)));
        }
        String before=json.write(state);var p=packet(state,history,List.of(),List.of(),6000);
        assertThat(p.blocked()).isFalse();assertThat(p.budget().estimated()).isLessThanOrEqualTo(6000);
        assertThat(json.write(p.input().get("state"))).contains("必须离线，周期改为四周");
        var selected=CustomerWorkflow.rows(p.input().get("history"));assertThat(selected.size()).isLessThan(120);assertThat(selected.size()%2).isZero();
        assertThat(selected.getFirst()).containsEntry("role","user");assertThat(selected.getLast()).containsEntry("messageId",120L);
        assertThat(history).hasSize(120);assertThat(json.write(state)).isEqualTo(before);
        assertThat(packet(state,history,List.of(),List.of(),6000).input()).isEqualTo(p.input());
    }
    @Test void overlargeProtectedRequirementsBlockInsteadOfBeingSilentlyTruncated(){
        var state=state();CustomerWorkflow.requirements(state).getFirst().put("text","不能联网且必须保留原有数据。".repeat(700));
        var p=packet(state,List.of(),List.of(),List.of(),6000);
        assertThat(p.blocked()).isTrue();assertThat(p.omissions()).contains("PROTECTED_INPUT_EXCEEDS_BUDGET");
        assertThatThrownBy(p::requireInput).hasMessage("CONTEXT_BUDGET_EXCEEDED");
        assertThat(json.write(p.input().get("state"))).contains("不能联网且必须保留原有数据。".repeat(700));
    }
    @Test void packetIsDeeplyImmutableAndManifestContainsOnlyIdentifiersAndAccounting(){
        var state=state();var p=packet(state,List.of(),List.of(source(state,3,"先别导入，四周只是估计。")),List.of(),6000);
        CustomerWorkflow.requirements(state).getFirst().put("text","外部修改");
        assertThat(json.write(p.input())).contains("必须离线，周期改为四周").doesNotContain("外部修改");
        assertThatThrownBy(()->p.input().put("message","篡改")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->CustomerWorkflow.map(p.input().get("state")).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(json.write(p.manifest())).contains("message:3").doesNotContain("先别导入","必须离线","其他要求不变");
    }
    @Test void archivedTaskRequirementsAreNotInjectedAsCurrentRequirements(){
        var state=state();CustomerWorkflow.workflow(state).put("archivedTasks",List.of(Map.of("id","old-task","status","PAUSED","requirements",List.of(Map.of("status","ACTIVE","text","允许联网旧目标".repeat(1000))))));
        var p=packet(state,List.of(Map.of("role","user","content","允许联网旧目标","taskId","old-task"),Map.of("role","assistant","content","已记录旧目标","taskId","old-task")),List.of(),List.of(),6000);
        assertThat(p.blocked()).isFalse();assertThat(json.write(p.input())).contains("old-task").doesNotContain("允许联网旧目标");
        assertThat(CustomerWorkflow.rows(p.input().get("history"))).isEmpty();assertThat(p.omissions()).contains("HISTORY_SCOPE");
    }
    @Test void currentTaskSummaryIsBackgroundAndOtherTaskSummaryIsExcluded(){
        var state=state();var foreign=new LinkedHashMap<>(summary(state,"foreign",List.of(5L,6L),"另一个任务允许联网"));foreign.put("taskId","other-task");
        var p=packet(state,List.of(),List.of(),List.of(summary(state,"good",List.of(1L,2L),"最初讨论两周期限，尚未导入。"),foreign),6000);
        assertThat(p.summaryRefs()).containsExactly("good");assertThat(json.write(p.input().get("summary"))).contains("HISTORICAL_BACKGROUND_NOT_CURRENT_FACTS","尚未导入").doesNotContain("允许联网");
        assertThat(json.write(p.input().get("state"))).contains("四周");assertThat(p.omissions()).contains("SUMMARY_SCOPE_OR_SOURCE");
    }
    @Test void summaryCannotClaimFutureSourceOrDuplicateRecentWindowCoverage(){
        var state=state();var history=List.<Map<String,Object>>of(Map.of("id",1L,"role","user","content","背景"),Map.of("id",2L,"role","assistant","content","回应"));
        var p=packet(state,history,List.of(),List.of(summary(state,"overlap",List.of(1L,2L),"重复背景"),summary(state,"future",List.of(201L,202L),"未来对话")),6000);
        assertThat(p.summaryRefs()).isEmpty();assertThat(p.omissions()).contains("SUMMARY_SCOPE_OR_SOURCE","SUMMARY_OVERLAP:overlap");
    }
    @Test void futureOrForeignRecoveredSourcesFailClosed(){
        var state=state();assertThat(packet(state,List.of(),List.of(source(state,201,"未来内容")),List.of(),6000).reason()).isEqualTo("INVALID_CONTEXT_SOURCE");
        var foreign=new LinkedHashMap<>(source(state,5,"别人的消息"));foreign.put("sessionId",2L);
        assertThat(packet(state,List.of(),List.of(foreign),List.of(),6000).blocked()).isTrue();
    }
    @Test void otherTaskSourceCanOnlyBeHistoricalReference(){
        var state=state();var other=new LinkedHashMap<>(source(state,5,"上个任务选择了甲项目"));other.put("taskId","old-task");
        var p=packet(state,List.of(),List.of(other),List.of(),6000);
        assertThat(CustomerWorkflow.rows(p.input().get("historicalSources")).getFirst()).containsEntry("historicalOnly",true);
        assertThat(json.write(p.input().get("state"))).doesNotContain("上个任务选择了甲项目");
    }
    @Test void incompleteTurnsAndBrokenToolPairsAreOmittedAsUnits(){
        var state=state();var history=List.<Map<String,Object>>of(Map.of("role","assistant","content","孤立回答"),Map.of("role","user","content","工具问题"),Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id","call-1"))),Map.of("role","assistant","content","缺少工具结果"));
        var p=packet(state,history,List.of(),List.of(),6000);assertThat(CustomerWorkflow.rows(p.input().get("history"))).isEmpty();
        assertThat(p.omissions()).contains("INCOMPLETE_HISTORY_TURN","INCOMPLETE_TOOL_HISTORY");
    }
    @Test void validToolRoundTripRemainsCompleteAndNeverBecomesOrphanResult(){
        var state=state();var history=List.<Map<String,Object>>of(Map.of("role","user","content","查询"),Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id","call-1"))),Map.of("role","tool","content","结果","tool_call_id","call-1"),Map.of("role","assistant","content","回答"));
        var p=packet(state,history,List.of(),List.of(),6000);assertThat(CustomerWorkflow.rows(p.input().get("history"))).hasSize(4);
        assertThat(packet(state,history,List.of(),List.of(),700).blocked()).isTrue();
    }
    @Test void scopeVersionMismatchBlocksBeforeModelInput(){
        var state=state();var p=CustomerContextAssembler.understanding("继续",state,"",List.of(),"",6000,json,Map.of(),new CustomerContextPacket.Scope(1,"other",3,200),List.of(),List.of());
        assertThat(p.reason()).isEqualTo("STALE_CONTEXT_SCOPE");assertThat(p.blocked()).isTrue();
    }
    @Test void fullInputBudgetCountsSystemSchemaReserveAndSerializedMetadata(){
        var check=CustomerContextBudget.check("ACTION","规则",Map.of("message","正文"),100,100,json);
        assertThat(check.blocked()).isTrue();assertThat(check.estimated()).isEqualTo(CustomerModel.estimate("规则"+json.write(Map.of("message","正文")))+100);
        assertThat(CustomerContextBudget.stageBudget("REQUIREMENT_MATCH",6000)).isEqualTo(4000);
        assertThatThrownBy(check::requireFits).hasMessage("CONTEXT_BUDGET_EXCEEDED");
    }
}
