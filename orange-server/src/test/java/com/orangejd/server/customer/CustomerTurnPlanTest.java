package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerTurnPlanTest {
    @Test void explanationAndChangeReachModelTogether() {
        assertThat(CustomerConditionFacts.route("用途核对是什么？把周期放宽到半年，继续推荐")).isNull();
        assertThat(CustomerConditionFacts.route("用途核对是什么")).isEqualTo("EXPLAIN");
        var action=Map.<String,Object>of("beforeTask",Map.of("intent","STATE","quote","当前有什么条件"));
        assertThat(CustomerTurnPlan.preamble(action,Map.of("language","Java"),"当前有什么条件？然后改成Python")).contains("Java");
        assertThat(CustomerTurnPlan.preamble(action,Map.of("language","Java"),"你好")).isEmpty();
    }
    @Test void questionAboutOrdinalBecomesScopedDetailAndFocusKeepsOriginalOrder() {
        var a=Map.<String,Object>of("id",1,"type","BEGINNER_PROJECT","name","a");
        var b=Map.<String,Object>of("id",2,"type","BEGINNER_PROJECT","name","b");
        var state=new LinkedHashMap<String,Object>(Map.of("lastCandidates",List.of(a,b)));
        var action=new LinkedHashMap<String,Object>(Map.of("intent","QUESTION","positions",List.of(1)));
        CustomerTurnPlan.resolve(action,state,"第二个怎么部署");
        assertThat(action).containsEntry("intent","DETAIL").containsEntry("positions",List.of(2));
        CustomerTurnPlan.focus(state,List.of(b),"run1");
        var follow=new LinkedHashMap<String,Object>(Map.of("intent","QUESTION"));
        CustomerTurnPlan.resolve(follow,state,"它需要Docker吗");
        assertThat(follow).containsEntry("positions",List.of(2));
        assertThat(state.get("lastCandidates")).isEqualTo(List.of(a,b));
    }
    @Test void ambiguousReferenceDoesNotInventIdentity() {
        var state=Map.<String,Object>of("lastCandidates",List.of(Map.of("id",1),Map.of("id",2)));
        var action=new LinkedHashMap<String,Object>(Map.of("intent","QUESTION"));
        CustomerTurnPlan.resolve(action,state,"这个项目怎么部署");
        assertThat(action).containsEntry("intent","DETAIL").doesNotContainKey("positions");
    }
    @Test void invalidTopicDoesNotDiscardValidDurationOrReply() {
        var action=Map.<String,Object>of("intent","CHAT","reply","可以继续聊","changes",Map.of(
            "topic",Map.of("value",Map.of("wrong",1),"quote","AI应用"),
            "maxDurationDays",Map.of("value",Map.of("quantity",6,"unit","MONTH","relation","AT_MOST"),"quote","半年内")));
        List<Map<String,Object>> rejected=new ArrayList<>();
        var state=CustomerState.reconcile(Map.of(),action,"AI应用，半年内完成","",rejected);
        assertThat(state).containsEntry("maxDurationDays",180);
        assertThat(rejected).hasSize(1);assertThat(CustomerDialogue.reply(action)).isEqualTo("可以继续聊");
    }
    @Test void questionInFirstClauseDoesNotTurnExplicitClearInSecondClauseIntoAnotherConfirmation() {
        var action=Map.<String,Object>of("intent","RECOMMEND","changes",Map.of("difficulty",Map.of("operation","CLEAR","quote","取消难度限制")));
        var state=CustomerState.reconcile(Map.of("difficulty","BEGINNER"),action,"用途核对是什么？取消难度限制，继续推荐","",new ArrayList<>());
        assertThat(state).doesNotContainKeys("difficulty","pendingConditions");
    }
    @Test void historyPreservesWholeTurnsAndValidStructuredAnswersWithSourcePointers() {
        var json=new CustomerJson(new ObjectMapper());List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=1;i<=8;i++) {
            rows.add(Map.of("id",i*2-1,"run_id","r"+i,"role","user","content","用户原话".repeat(100)));
            rows.add(Map.of("id",i*2,"run_id","r"+i,"role","assistant","content",json.write(Map.of("answer","回答".repeat(400),"status","CONVERSATION"))));
        }
        Collections.reverse(rows);var view=CustomerHistory.build(rows,"current",1400,json);
        assertThat(view.messages().size()%2).isZero();assertThat(view.messages()).isNotEmpty();
        view.messages().stream().filter(r->"assistant".equals(r.get("role"))).forEach(r->assertThat(json.read(r.get("content"))).containsKey("answer"));
        assertThat(json.read(view.summary())).containsEntry("kind","USER_EXCERPTS_NOT_CONFIRMED_CONDITIONS");
        assertThat(view.through()).isPositive();assertThat(rows).hasSize(16);
    }
    @Test void protocolSuccessIsNotBusinessSuccessAndPhaseAccountingIncludesFailures() {
        assertThat(CustomerOutcome.classify(Map.of("status","RELEVANCE_UNAVAILABLE"))).isEqualTo("INCOMPLETE");
        assertThat(CustomerOutcome.classify(Map.of("status","UNKNOWN"))).isEqualTo("UNASSESSED");
        var calls=List.<Map<String,Object>>of(Map.of("phase","ACTION","status","FAILED","durationMs",100),Map.of("phase","ACTION","status","SUCCEEDED","usage",Map.of("total_tokens",10,"prompt_tokens",7,"completion_tokens",3),"durationMs",200));
        var usage=(Map<?,?>)CustomerUsage.byPhase(calls).get("ACTION");
        assertThat(usage.get("durationMs")).isEqualTo(300L);assertThat(usage.get("complete")).isEqualTo(false);
        assertThat(usage.get("reasoningUsageComplete")).isEqualTo(false);
    }
    @Test void explicitExclusionIsRememberedByRepositoryAndCanBeReset() {
        var card=Map.<String,Object>of("id",1,"type","EXTERNAL_PROJECT","name","demo/app","url","https://github.com/demo/app");
        var state=new HashMap<String,Object>(Map.of("lastCandidates",List.of(card)));
        CustomerProjectFeedback.apply(state,List.of(Map.of("operation","DISMISS","position",1,"quote","不要第一个")),"不要第一个，换一批","r");
        assertThat(CustomerProjectFeedback.excluded(card,state)).isTrue();
        var alias=new HashMap<>(card);alias.put("type","BEGINNER_PROJECT");alias.put("id",8);
        assertThat(CustomerProjectFeedback.excluded(alias,state)).isTrue();
        CustomerProjectFeedback.apply(state,List.of(Map.of("operation","RESET","position",0,"quote","恢复全部")),"恢复全部","r2");
        assertThat(CustomerProjectFeedback.excluded(card,state)).isFalse();
        CustomerProjectFeedback.apply(state,List.of(Map.of("operation","DISMISS","position",1,"quote","不要第一个")),"第一个如何部署","r3");
        assertThat(CustomerProjectFeedback.excluded(card,state)).isFalse();
    }
}
