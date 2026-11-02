package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerEvidenceGapScopeTest extends CustomerWorkflowMatchingTest {
    private void oneKnownProject(){
        turn.cards.getFirst().put("references",List.of(Map.of("evidenceId","E1","sourceRef","one","quote","可以离线运行。")));
        turn.references=CustomerWorkflow.rows(turn.cards.getFirst().get("references"));
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"spans",List.of(Map.of("spanId","E1.S1","text","可以离线运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:1","requirementId",requirement.get("id"),"verdict","SATISFIED","evidenceId","E1","spanId","E1.S1"))));
        when(model.call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList())).thenReturn(Map.of("supportedIds",List.of(0)));
    }
    @Test void rejectedUnknownCandidateDoesNotPolluteTheVerifiedPresentedProject(){
        oneKnownProject();turn.state.put("verificationMode","STRICT");CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.result).containsEntry("status","VERIFIED_PROJECTS");assertThat(turn.cards).hasSize(1);
        assertThat(CustomerWorkflow.rows(turn.result.get("references"))).hasSize(1).allMatch(r->"one".equals(r.get("sourceRef")));
        CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");CustomerEvidenceDecision.apply(turn.state,turn.result,"RECOMMEND");
        var d=CustomerWorkflow.map(turn.result.get("evidenceDecision"));assertThat(d).containsEntry("status","SUPPORTED");
        assertThat(CustomerWorkflow.rows(d.get("projectFacts"))).isEmpty();
    }
    @Test void noPresentedProjectStillRetainsTheCandidatePoolsUnknownFacts(){
        turn.state.put("verificationMode","STRICT");CustomerRequirementMatcher.match(c,turn);
        CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");CustomerEvidenceDecision.apply(turn.state,turn.result,"RECOMMEND");
        var d=CustomerWorkflow.map(turn.result.get("evidenceDecision"));assertThat(d).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
        assertThat(CustomerWorkflow.rows(d.get("projectFacts"))).hasSize(2);
    }
    @Test void knownBeginnerProfileDoesNotBecomeAnotherUserQuestionWhenLearningScopeIsUnknown(){
        oneKnownProject();turn.state.put("verificationMode","STRICT");turn.state.put("experience","Java初学者");
        turn.cards.getFirst().put("codebaseSize","LARGE");turn.cards.getFirst().put("difficulty","入门");
        CustomerRequirementMatcher.match(c,turn);CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");CustomerEvidenceDecision.apply(turn.state,turn.result,"RECOMMEND");
        var d=CustomerWorkflow.map(turn.result.get("evidenceDecision"));
        assertThat(d).containsEntry("status","PROJECT_FACTS_UNVERIFIED");assertThat(CustomerWorkflow.rows(d.get("userInputs"))).isEmpty();
        assertThat(CustomerWorkflow.rows(d.get("projectFacts"))).anyMatch(g->"LEARNING_SCOPE_UNVERIFIED".equals(g.get("reason")));
        assertThat(turn.result).doesNotContainKey("pendingQuestion");assertThat(turn.state).containsEntry("experience","Java初学者");
    }
    @Test void legacyCardWithUnknownConditionsIsNotFullySupportedJustBecauseItHasOneCitation(){
        var card=new LinkedHashMap<String,Object>(Map.of("type","EXTERNAL_PROJECT","id",1,"name","one","matchStatus","NEEDS_VERIFICATION", "references",List.of(Map.of("evidenceId","E1"))));
        var r=new LinkedHashMap<String,Object>(Map.of("status","PROJECTS_WITH_LIMITATIONS","projects",List.of(card),"references",List.of(Map.of("evidenceId","E1"))));
        CustomerEvidenceDecision.apply(turn.state,r,"RECOMMEND");
        assertThat(CustomerWorkflow.map(r.get("evidenceDecision"))).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
    }
}
