package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerSemanticContinuationTest {
    Map<String,Object> action(String quote,Object value) { return Map.of("intent","RECOMMEND","changes",Map.of("maxDurationDays",Map.of("value",value,"quote",quote))); }
    Map<String,Object> duration(double quantity,String unit) { return Map.of("quantity",quantity,"unit",unit,"relation","AT_MOST"); }
    @Test void modelSemanticQuantityIsConvertedByServerAcrossPhrasings() {
        for(var c:List.of(Map.entry("半年内",duration(6,"MONTH")),Map.entry("一个半月搞定",duration(1.5,"MONTH")),Map.entry("一个季度之内",duration(3,"MONTH")),Map.entry("a fortnight",duration(2,"WEEK")))) {
            var result=CustomerState.reconcile(Map.of(),action(c.getKey(),c.getValue()),c.getKey(),"",new ArrayList<>());
            assertThat(result.get("maxDurationDays")).isEqualTo(CustomerDuration.normalize(c.getValue()));
            assertThat(result).doesNotContainKey("pendingConditions");
        }
    }
    @Test void malformedOrAmbiguousConditionBecomesPendingWhileOtherFieldsPersist() {
        var a=new LinkedHashMap<>(action("每天两小时",duration(2,"DAY")));
        a.put("changes",Map.of("language",Map.of("value","Java","quote","Java"),"maxDurationDays",Map.of("value",duration(2,"DAY"),"quote","每天两小时")));
        var result=CustomerState.reconcile(Map.of("allowDocker",false),a,"Java，每天两小时","",new ArrayList<>());
        assertThat(result).containsEntry("language","Java").containsEntry("allowDocker",false).containsKey("pendingConditions").doesNotContainKey("maxDurationDays");
        var clarified=CustomerState.reconcile(result,action("三个月内",duration(3,"MONTH")),"三个月内","",new ArrayList<>());
        assertThat(clarified).containsEntry("maxDurationDays",90).doesNotContainKey("pendingConditions");
    }
    @Test void knownContradictionDoesNotSilentlyReplaceExistingDuration() {
        var result=CustomerState.reconcile(Map.of("maxDurationDays",30),action("两周以内",duration(6,"MONTH")),"两周以内","",new ArrayList<>());
        assertThat(result).containsEntry("maxDurationDays",30).containsKey("pendingConditions");
    }
    @Test void rangeLowerBoundAndUncertainSemanticsCannotBecomeUpperBound() {
        assertThatThrownBy(()->CustomerDuration.normalize(Map.of("quantity",6,"unit","MONTH","relation","AT_LEAST"))).hasMessage("AMBIGUOUS_DURATION_RELATION");
        assertThat(CustomerDuration.estimate("至少两个月")).isNull();
        assertThat(CustomerDuration.estimate("每天2小时")).isNull();
        assertThat(CustomerDuration.estimate("三到四个月")).isEqualTo(120);
        assertThat(CustomerDuration.estimate("十二周")).isEqualTo(84);
        assertThat(CustomerDuration.estimate("2 months")).isEqualTo(60);
    }
    @Test void synonymsAndDoubleNegationDoNotRequireLiteralCanonicalSpelling() {
        var action=Map.<String,Object>of("intent","COLLECT","changes",Map.of("language",Map.of("value","JavaScript","quote","JS"),"allowDocker",Map.of("value",true,"quote","不是不能使用Docker")));
        var result=CustomerState.reconcile(Map.of("allowDocker",false),action,"JS，不是不能使用Docker","",new ArrayList<>());
        assertThat(result).containsEntry("language","JavaScript").containsEntry("allowDocker",true).doesNotContainKey("pendingConditions");
    }
    @Test void allTerminalStatesHaveContinuationTextWithoutPretendingSuccess() {
        for(String status:List.of("FAILED","TIMED_OUT","CANCELLED","INTERRUPTED"))
            assertThat(CustomerRecovery.message(status,"DEPENDENCY_FAILED")).isNotBlank().doesNotContain("本轮失败，可以重新提问");
        assertThatThrownBy(()->CustomerRecovery.requireRecoverable(new IllegalStateException("RUN_STOPPED"))).hasMessage("RUN_STOPPED");
    }
    @Test void unrelatedNullProposalDoesNotCreateAnInventedQuestion() {
        Map<String,Object> changes=new HashMap<>(); changes.put("allowDocker",null);
        var action=Map.<String,Object>of("intent","COLLECT","changes",changes,"pendingConditions",List.of(Map.of("field","maxDurationDays","quote","二十个工作日","question","你希望按多少自然日筛选？")));
        var state=CustomerState.reconcile(Map.of(),action,"二十个工作日","",new ArrayList<>());
        assertThat((List<?>)state.get("pendingConditions")).hasSize(1);
        assertThat(((Map<?,?>)((List<?>)state.get("pendingConditions")).getFirst()).get("field")).isEqualTo("maxDurationDays");
    }
    @Test void conflictingBoundsAreClarifiedAndCanBeResolvedWithoutLosingOtherConditions() {
        var conflict=CustomerState.reconcile(Map.of("minDurationDays",90,"language","Java"),action("两个月内",duration(2,"MONTH")),"两个月内","",new ArrayList<>());
        assertThat(conflict).containsKey("pendingConditions");
        var resolved=CustomerState.reconcile(conflict,action("六个月内",duration(6,"MONTH")),"六个月内","",new ArrayList<>());
        assertThat(resolved).containsEntry("minDurationDays",90).containsEntry("maxDurationDays",180).containsEntry("language","Java").doesNotContainKey("pendingConditions");
    }
    @Test void explicitDismissalOnlyRemovesPendingItemAndKeepsConfirmedHardConditions() {
        var prior=Map.<String,Object>of("language","Java","allowDocker",false,"pendingConditions",List.of(Map.of("field","other","quote","电脑只有8G","question","内存限制如何核对？")));
        var action=Map.<String,Object>of("intent","CHAT","changes",Map.of(),"pendingConditions",List.of(Map.of("field","other","quote","先不考虑内存限制","question","","resolved",true)));
        var state=CustomerState.reconcile(prior,action,"先不考虑内存限制","",new ArrayList<>());
        assertThat(state).containsEntry("language","Java").containsEntry("allowDocker",false).doesNotContainKey("pendingConditions");
    }
}
