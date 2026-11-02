package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerEvidenceGapMatchingTest extends CustomerWorkflowMatchingTest {
    @Test void strictDiscardedUnknownCandidatesKeepTheirFactGapInsteadOfRequestingUserConfirmation(){
        turn.state.put("verificationMode","STRICT");CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.cards).isEmpty();assertThat(CustomerWorkflow.rows(turn.result.get("verificationGaps"))).hasSize(2);
        CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");CustomerEvidenceDecision.apply(turn.state,turn.result,"RECOMMEND");
        assertThat(CustomerWorkflow.map(turn.result.get("evidenceDecision"))).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
        assertThat(turn.result).doesNotContainKey("pendingQuestion");
        assertThat(requirement).containsEntry("text","必须离线运行").containsEntry("strength","HARD");
    }
    @Test void choosingExplorationDoesNotCertifyAnUnknownProjectFactOrGrantImport(){
        CustomerRequirementMatcher.match(c,turn);CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");
        var q=CustomerWorkflow.question(turn.state);
        var action=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","questionAnswer",Map.of("questionId",q.get("id"),"quote","先考察待核实候选")));
        CustomerTaskService.apply(turn.state,action,"先考察待核实候选","m2","r2",CustomerReferenceResolver.resolve("先考察待核实候选",turn.state,Map.of()));
        CustomerTaskService.finish(turn.state,turn.result,"COLLECT","r2");CustomerEvidenceDecision.apply(turn.state,turn.result,"COLLECT");
        assertThat(CustomerWorkflow.question(turn.state)).isEmpty();
        assertThat(turn.cards).allMatch(card->Boolean.FALSE.equals(card.get("importable")));
        assertThat(CustomerWorkflow.map(turn.result.get("evidenceDecision"))).containsEntry("status","PROJECT_FACTS_UNVERIFIED");
        assertThat(requirement).containsEntry("status","ACTIVE").containsEntry("strength","HARD");
    }
    @Test void failedRequirementVerificationDoesNotCreateATradeoffQuestion(){
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"spans",List.of(Map.of("spanId","E1.S1","text","本项目提供本地数据处理功能。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenThrow(new IllegalStateException("PROVIDER_HTTP_503"));
        CustomerRequirementMatcher.match(c,turn);CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");CustomerEvidenceDecision.apply(turn.state,turn.result,"RECOMMEND");
        assertThat(CustomerWorkflow.question(turn.state)).isEmpty();assertThat(turn.result).doesNotContainKey("pendingQuestion");
        assertThat(CustomerWorkflow.map(turn.result.get("evidenceDecision"))).containsEntry("status","VERIFICATION_UNAVAILABLE");
    }
}
