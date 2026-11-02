package com.orangejd.server.customer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CustomerSourceCategoryClarificationTest {
    @Test void invalidSoftGoalProposalKeepsPreviousPreferenceWithoutBlockingTheBusinessTopic() {
        String message="给小店找库存管理系统";
        var action=Map.<String,Object>of("intent","COLLECT","changes",Map.of(
            "goal",Map.of("value","自用","quote","给小店"),
            "topic",Map.of("value",List.of("库存管理"),"quote","库存管理系统")));
        var rejected=new ArrayList<Map<String,Object>>();
        var state=CustomerState.reconcile(Map.of("goal","学习"),action,message,"",rejected);
        assertThat(state).containsEntry("goal","学习").containsKey("topic").doesNotContainKey("pendingConditions");
        assertThat(rejected).anyMatch(r->"goal".equals(r.get("field")));
    }
    @Test void careerGoalDoesNotCreateAQuestionAboutAnUnrequestedCategory() {
        String message="我想做Java求职项目，先帮我确认一个最关键的问题。";
        var rejected=new ArrayList<Map<String,Object>>();
        var action=Map.<String,Object>of("intent","COLLECT","changes",Map.of(
            "goal",Map.of("value","求职","quote","求职项目"),
            "sourceType",Map.of("value","CAREER_PROJECT","quote","求职项目")),
            "pendingConditions",List.of(Map.of("field","sourceType","quote","求职项目","question","选哪个专区？")));
        var state=CustomerState.reconcile(Map.of(),action,message,"",rejected);
        assertThat(state).containsEntry("goal","求职").doesNotContainKeys("sourceType","pendingConditions");
        assertThat(rejected).anyMatch(r->"sourceType".equals(r.get("field")));
    }
    @Test void explicitlyAmbiguousCategoryStillNeedsClarification() {
        var action=Map.<String,Object>of("intent","COLLECT","changes",Map.of("sourceType",Map.of("value","UNKNOWN","quote","只看那个专区")));
        var state=CustomerState.reconcile(Map.of(),action,"只看那个专区","",new ArrayList<>());
        assertThat(state).containsKey("pendingConditions").doesNotContainKey("sourceType");
    }
}
