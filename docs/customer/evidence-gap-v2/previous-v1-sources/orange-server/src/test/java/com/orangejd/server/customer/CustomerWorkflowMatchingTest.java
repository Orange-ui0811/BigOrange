package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerWorkflowMatchingTest {
    CustomerModel model=mock(CustomerModel.class);CustomerProjects projects=mock(CustomerProjects.class);CustomerTools.Context context=mock(CustomerTools.Context.class);
    CustomerConversation c=new CustomerConversation(mock(CustomerStore.class),mock(CustomerMapper.class),new CustomerJson(new ObjectMapper()),model,mock(CustomerTools.class),projects,new CustomerProperties());
    CustomerConversation.Turn turn=c.newTurn(new CustomerRunContext("r",Instant.now().plusSeconds(90),new ArrayList<>()));
    Map<String,Object> requirement;
    @BeforeEach void setup(){
        turn.state=new LinkedHashMap<>();CustomerWorkflow.initialize(turn.state,List.of(),"r");turn.result=new LinkedHashMap<>();turn.context=context;
        requirement=CustomerRequirementService.entry(CustomerWorkflow.task(turn.state),"open","必须离线运行","必须离线运行","HARD","m","必须离线运行","MESSAGE");CustomerWorkflow.task(turn.state).put("requirements",List.of(requirement));
        turn.cards=List.of(new LinkedHashMap<>(Map.of("type","EXTERNAL_PROJECT","id",1,"sourceRef","one","name","one","importable",true)),new LinkedHashMap<>(Map.of("type","EXTERNAL_PROJECT","id",2,"sourceRef","two","name","two","importable",true)));
    }
    @Test void exploratoryKeepsUnknownButDisablesImport(){CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).hasSize(2).allMatch(card->Boolean.FALSE.equals(card.get("importable")));assertThat(CustomerWorkflow.rows(turn.cards.getFirst().get("requirementMatches")).getFirst()).containsEntry("verdict","UNKNOWN");verifyNoInteractions(model);}
    @Test void strictNeverTreatsUnknownAsSatisfied(){turn.state.put("verificationMode","STRICT");CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).isEmpty();assertThat(turn.result).containsEntry("status","NO_VERIFIED_MATCH");}
    @Test void candidateUncertaintyHasOneQuestionRatherThanAnExtraRelaxationPrompt(){
        CustomerRequirementMatcher.match(c,turn);
        new CustomerRecommendationSteps(c).finishRecommendation(turn);
        CustomerTaskService.finish(turn.state,turn.result,"RECOMMEND","r");
        verify(context,never()).diagnose(anyMap());
        assertThat(turn.result.get("pendingQuestion").toString()).contains("离线运行");
        assertThat(CustomerWorkflow.rows(CustomerWorkflow.task(turn.state).get("questions"))).filteredOn(q->"OPEN".equals(q.get("status"))).hasSize(1);
    }
    @Test void sourceCannotSupportAnotherProject(){
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"content","可以离线运行。","spans",List.of(Map.of("spanId","E1.S1","text","可以离线运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:2","requirementId",requirement.get("id"),"verdict","SATISFIED","evidenceId","E1","spanId","E1.S1"))));
        CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).allMatch(card->"UNKNOWN".equals(CustomerWorkflow.rows(card.get("requirementMatches")).getFirst().get("verdict")));verify(model,never()).call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList());
    }
    @Test void verifiedHardConflictIsExcludedEvenInExploratoryMode(){
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"content","必须联网才能运行。","spans",List.of(Map.of("spanId","E1.S1","text","必须联网才能运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:1","requirementId",requirement.get("id"),"verdict","CONFLICT","evidenceId","E1","spanId","E1.S1","reason","必须联网"))));
        when(model.call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList())).thenReturn(Map.of("supportedIds",List.of(0)));
        CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).hasSize(1);assertThat(turn.cards.getFirst()).containsEntry("id",2);
    }
    @Test void largeEvidenceAndManyRequirementsHaveBoundedPayloadAndPairCount(){
        var requirements=new ArrayList<Map<String,Object>>();
        for(int i=0;i<30;i++)requirements.add(Map.of("id","r"+i,"field","open","text","必须支持离线处理"+i));
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","spans",List.of(Map.of("spanId","E1.S1","text","离线运行。".repeat(2000)),Map.of("spanId","E1.S2","text","可以离线运行。"))));
        var input=CustomerRequirementMatcher.matchingInput(c,turn,requirements);
        assertThat(CustomerModel.estimate(CustomerRequirementMatcher.PROMPT+c.json.write(input))).isLessThanOrEqualTo(c.config.getRequirementInputTokens());
        assertThat(CustomerWorkflow.rows(input.get("requirements"))).hasSize(12);
        assertThat(c.json.write(input)).doesNotContain("离线运行。".repeat(121));
    }
    @Test void omittedRequirementCannotBePromotedByHallucinatedResponse(){
        requirement.put("text","需要".repeat(4000));
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","spans",List.of(Map.of("spanId","E1.S1","text","可以离线运行。"))));
        CustomerRequirementMatcher.match(c,turn);verifyNoInteractions(model);
        assertThat(turn.cards).allMatch(card->"UNKNOWN".equals(CustomerWorkflow.rows(card.get("requirementMatches")).getFirst().get("verdict")));
    }
    @Test void unsentSpanCannotBeUsedEvenWhenItExistsInRetrievedEvidence(){
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"spans",List.of(Map.of("spanId","E1.S1","text","本项目需要联网。"),Map.of("spanId","E1.S2","text","可以离线运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:1","requirementId",requirement.get("id"),"verdict","SATISFIED","evidenceId","E1","spanId","E1.S2"))));
        CustomerRequirementMatcher.match(c,turn);verify(model,never()).call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList());
        assertThat(turn.cards).allMatch(card->Boolean.FALSE.equals(card.get("importable")));
    }
    @Test void verificationUsesOnlyCompactChecksAndRejectsFractionalIndex(){
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"spans",List.of(Map.of("spanId","E1.S1","text","可以离线运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:1","requirementId",requirement.get("id"),"verdict","SATISFIED","evidenceId","E1","spanId","E1.S1"))));
        when(model.call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList())).thenAnswer(i->{
            Map<String,Object> input=i.getArgument(1);assertThat(CustomerWorkflow.rows(input.get("checks")).getFirst()).containsOnlyKeys("requirement","quote","verdict");
            assertThat(CustomerModel.estimate(CustomerRequirementMatcher.VERIFY_PROMPT+c.json.write(input))).isLessThanOrEqualTo(c.config.getRequirementInputTokens());
            return Map.of("supportedIds",List.of(0.5,99,-1));
        });
        CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).allMatch(card->Boolean.FALSE.equals(card.get("importable")));
    }
    @Test void strictEmptyResultSkipsAdviceNode(){
        turn.state.put("verificationMode","STRICT");turn.step("match_requirements");
        assertThat(turn.next()).isEqualTo("finalize_response");assertThat(turn.result).containsEntry("status","NO_VERIFIED_MATCH");verifyNoInteractions(model);
    }
    @Test void strictKeepsOnlyProjectWithVerifiedPositiveEvidence(){
        turn.state.put("verificationMode","STRICT");
        turn.evidence=List.of(Map.of("evidenceId","E1","sourceRef","one","documentId",1L,"versionId",1L,"spans",List.of(Map.of("spanId","E1.S1","text","可以离线运行。"))));
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:1","requirementId",requirement.get("id"),"verdict","SATISFIED","evidenceId","E1","spanId","E1.S1"))));
        when(model.call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList())).thenReturn(Map.of("supportedIds",List.of(0)));
        CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).hasSize(1);assertThat(turn.cards.getFirst()).containsEntry("id",1);
        assertThat(CustomerWorkflow.rows(turn.cards.getFirst().get("requirementMatches")).getFirst()).containsEntry("verdict","SATISFIED");
    }
}
