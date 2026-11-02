package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerConditionOperationsTest {
    Map<String,Object> action(String field,String quote) {return Map.of("intent","COLLECT","changes",Map.of(field,Map.of("operation","CLEAR","quote",quote)));}
    Map<String,Object> update(Map<String,Object> state,Map<String,Object> action,String message) {return CustomerState.reconcile(state,action,message,"",new ArrayList<>());}
    @Test void removeAbsentDifficultyIsNoOpAndDoesNotRemoveExperience() {
        String message="不再限定“刚学”对应的难度";var prior=Map.<String,Object>of("experience","刚学","language","Java");
        var next=update(prior,action("difficulty",message),message);
        assertThat(next).containsEntry("experience","刚学").containsEntry("language","Java").doesNotContainKeys("difficulty","pendingConditions");
        assertThat(CustomerConditionFacts.changes(prior,next,action("difficulty",message))).contains("本来就没有");
    }
    @Test void confirmationExecutesOnlyStoredOperationAndCancellationKeepsOriginal() {
        String question="要不要取消难度限制？";var prior=Map.<String,Object>of("difficulty","初级","language","Java");
        var pending=update(prior,action("difficulty",question),question);
        assertThat(pending).containsEntry("difficulty","初级").containsKey("pendingConditions");
        var confirmed=update(pending,Map.of("intent","STATE"),"确认");
        assertThat(confirmed).doesNotContainKeys("difficulty","pendingConditions").containsEntry("language","Java");
        var cancelled=update(pending,Map.of("intent","STATE"),"先不改");
        assertThat(cancelled).containsEntry("difficulty","初级").doesNotContainKey("pendingConditions");
    }
    @Test void bareConfirmationCannotReplacePendingQuoteOrGuessInvalidValue() {
        var prior=Map.<String,Object>of("pendingConditions",List.of(Map.of("field","maxDurationDays","quote","十来周","question","具体多少周？")));
        assertThat(update(prior,Map.of("intent","STATE"),"确认")).isEqualTo(prior);
    }
    @Test void changedConditionInvalidatesPendingOperation() {
        String question="是否取消难度限制？";var pending=update(Map.of("difficulty","初级"),action("difficulty",question),question);
        pending.put("difficulty","高级");assertThat(update(pending,Map.of("intent","STATE"),"确认")).containsEntry("difficulty","高级");
    }
    @Test void legacyClearPendingCanBeConfirmedWithoutLooping() {
        var prior=Map.<String,Object>of("experience","刚学","pendingConditions",List.of(Map.of("field","difficulty","quote","不再限定刚学对应的难度","question","确认要求")));
        assertThat(update(prior,Map.of("intent","STATE"),"确认")).doesNotContainKeys("difficulty","pendingConditions").containsEntry("experience","刚学");
    }
    @Test void clearedProfileDefaultDoesNotReturnNextTurn() {
        var next=update(Map.of("language","Java"),action("language","语言不限"),"语言不限");
        var after=CustomerState.reconcile(next,Map.of("intent","CHAT","profileDefaults",Map.of("language",Map.of("value","Java","quote","Java"))),"你好","Java",new ArrayList<>());
        assertThat(after).doesNotContainKey("language");
    }
    @Test void negativeCancellationAndWrongFieldCannotRelax() {
        assertThat(update(Map.of("difficulty","初级"),action("difficulty","不要取消难度限制"),"不要取消难度限制")).containsEntry("difficulty","初级");
        assertThat(update(Map.of("language","Java"),action("language","取消Docker限制"),"取消Docker限制")).containsEntry("language","Java");
        String mixed="不要取消Java语言限制，只去掉难度限制";
        assertThat(update(Map.of("language","Java","difficulty","初级"),action("difficulty",mixed),mixed)).containsEntry("language","Java").doesNotContainKeys("difficulty","pendingConditions");
    }
    @Test void systemQuestionsHaveDeterministicRoutes() {
        assertThat(CustomerConditionFacts.route("当前有什么条件")).isEqualTo("STATE");
        assertThat(CustomerConditionFacts.route("为什么条件没有放宽")).isEqualTo("STATE");
        assertThat(CustomerConditionFacts.route("为什么没有推荐项目")).isEqualTo("DIAGNOSE");
        assertThat(CustomerConditionFacts.route("把周期放宽到半年")).isNull();
    }
}
