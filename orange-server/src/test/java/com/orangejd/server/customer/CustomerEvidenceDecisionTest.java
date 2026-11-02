package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerEvidenceDecisionTest {
    private Map<String,Object> state(){var s=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(s,List.of(),"r");return s;}
    private Map<String,Object> decision(Map<String,Object> s,Map<String,Object> result,String intent) {
        CustomerEvidenceDecision.apply(s,result,intent);return CustomerWorkflow.map(result.get("evidenceDecision"));
    }
    @Test void currentUserConditionQuestionIsAUserGap() {
        var s=state();CustomerTaskService.ask(s,"REQUIREMENT","你想直接部署，还是二次开发？",List.of(),"usage","r");
        var r=new LinkedHashMap<String,Object>(Map.of("status","FOLLOW_UP","question",CustomerWorkflow.question(s)));
        assertThat(decision(s,r,"COLLECT")).containsEntry("status","USER_INPUT_REQUIRED").containsEntry("nextAction","ASK_USER");
        assertThat(CustomerWorkflow.rows(CustomerWorkflow.map(r.get("evidenceDecision")).get("projectFacts"))).isEmpty();
    }
    @Test void explicitKnownRequirementWithUnknownProjectMatchIsAProjectFactGap() {
        var s=state();var gap=Map.of("owner","PROJECT_FACT","text","必须离线运行","reason","缺原文","requirementId","req1");
        var r=new LinkedHashMap<String,Object>(Map.of("status","NO_VERIFIED_MATCH","verificationGaps",List.of(gap)));
        assertThat(decision(s,r,"RECOMMEND")).containsEntry("status","PROJECT_FACTS_UNVERIFIED").containsEntry("nextAction","VERIFY_PROJECT_FACTS");
        assertThat(s).doesNotContainKey("verificationMode");
    }
    @Test void acceptingUncertainCandidatesIsAChoiceAndDoesNotClearTheProjectFactGap() {
        var s=state();CustomerTaskService.ask(s,"TRADEOFF","愿意先考察待核实候选吗？",List.of(),"tradeoff","r");
        var r=new LinkedHashMap<String,Object>(Map.of("status","PROJECTS_WITH_LIMITATIONS","question",CustomerWorkflow.question(s),
                "verificationGaps",List.of(Map.of("text","离线支持","owner","PROJECT_FACT"))));
        var d=decision(s,r,"RECOMMEND");
        assertThat(d).containsEntry("status","USER_DECISION_REQUIRED").containsEntry("nextAction","ASK_PREFERENCE");
        assertThat(CustomerWorkflow.rows(d.get("projectFacts"))).hasSize(1);assertThat(CustomerWorkflow.rows(d.get("userInputs"))).isEmpty();
    }
    @Test void outageDoesNotBecomeMissingUserInformation() {
        var s=state();CustomerTaskService.ask(s,"REQUIREMENT","用途？",List.of(),"goal","r");
        var r=new LinkedHashMap<String,Object>(Map.of("status","KNOWLEDGE_UNAVAILABLE","question",CustomerWorkflow.question(s),"pendingQuestion","用途？"));
        assertThat(decision(s,r,"QUESTION")).containsEntry("status","VERIFICATION_UNAVAILABLE").containsEntry("nextAction","RETRY_VERIFICATION");
        assertThat(r).doesNotContainKeys("pendingQuestion","question");assertThat(CustomerWorkflow.question(s)).isNotEmpty();
    }
    @Test void unrelatedOldQuestionIsNotInjectedIntoFactQa() {
        var s=state();CustomerTaskService.ask(s,"REQUIREMENT","你有多少时间？",List.of(),"time","old");
        var r=new LinkedHashMap<String,Object>(Map.of("status","REFUSE_NO_MATCH","projects",List.of()));
        CustomerTaskService.finish(s,r,"QUESTION","now");
        assertThat(r).doesNotContainKeys("pendingQuestion","question");assertThat(CustomerWorkflow.question(s)).containsEntry("status","OPEN");
    }
    @Test void noCandidatesAndMissingEvidenceAreDifferentOutcomes() {
        var r=new LinkedHashMap<String,Object>(Map.of("status","NO_MATCH"));
        assertThat(decision(state(),r,"RECOMMEND")).containsEntry("status","NO_VERIFIED_CANDIDATE");
        var failed=new LinkedHashMap<String,Object>(Map.of("status","PURPOSE_REVIEW_PARTIAL"));
        assertThat(decision(state(),failed,"RECOMMEND")).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
    }
    @Test void simultaneousUserAndFactGapsRemainSeparate() {
        var s=state();CustomerTaskService.ask(s,"REQUIREMENT","你准备在哪个系统部署？",List.of(),"os","r");
        var r=new LinkedHashMap<String,Object>(Map.of("status","FOLLOW_UP","question",CustomerWorkflow.question(s),"verificationGaps",List.of(Map.of("text","支持的平台","owner","PROJECT_FACT"))));
        var d=decision(s,r,"COLLECT");assertThat(d).containsEntry("status","MIXED_GAPS");
        assertThat(CustomerWorkflow.rows(d.get("userInputs"))).hasSize(1);assertThat(CustomerWorkflow.rows(d.get("projectFacts"))).hasSize(1);
    }
}
