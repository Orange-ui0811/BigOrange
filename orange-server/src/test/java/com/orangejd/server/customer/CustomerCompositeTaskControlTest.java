package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

class CustomerCompositeTaskControlTest {
    Map<String,Object> state=new LinkedHashMap<>();
    CustomerCompositeTaskControlTest(){initialize(state,List.of(),"first");var r=CustomerRequirementService.entry(task(state),"open","必须离线运行","必须离线运行","HARD","m","必须离线运行","MESSAGE");task(state).put("requirements",List.of(r));}
    Map<String,Object> control(String op,String raw){
        var a=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","taskAction",Map.of("operation",op,"quote",raw)));
        CustomerTaskService.prepare(state,a,raw,"test");CustomerTaskService.apply(state,a,raw,"message","test",CustomerReferenceResolver.resolve(raw,state,Map.of()));return a;
    }
    @Test void switchThenRecordDoesNotOverwriteOriginalTask(){
        String original=task(state).get("id").toString();
        var a=control("SWITCH_TASK","换个任务，现在改找Python项目，先记录，不要推荐。");
        assertThat(task(state).get("id")).isNotEqualTo(original);assertThat(active(state)).isEmpty();
        assertThat(rows(workflow(state).get("archivedTasks")).getFirst()).containsEntry("id",original).containsEntry("status","PAUSED");
        assertThat(a.get("workflowReply").toString()).contains("切换","不搜索");
    }
    @Test void resumeThenRecordRestoresOriginalRequirementsWithoutSearching(){
        String original=task(state).get("id").toString();control("SWITCH_TASK","换个任务，现在找Python项目，先记录。");
        var a=control("RESUME_TASK","恢复之前那个任务，其他要求不变，不要搜索。");
        assertThat(task(state)).containsEntry("id",original);assertThat(active(state)).hasSize(1);assertThat(active(state).getFirst()).containsEntry("text","必须离线运行");
        assertThat(a.get("workflowReply").toString()).contains("恢复","不搜索");
    }
    @Test void missingResumeTargetStillRequestsClarificationWhenSearchIsForbidden(){
        var a=control("RESUME_TASK","恢复之前的任务，不要搜索。");
        assertThat(a.get("workflowReply").toString()).contains("请说明要恢复").doesNotContain("已恢复");
    }
}
