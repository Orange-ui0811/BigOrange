package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Replays the semantic boundary exposed by the real ToolJet inventory recommendation. */
class CustomerRecommendationFitTest {
    CustomerModel model=mock(CustomerModel.class);CustomerTools.Context context=mock(CustomerTools.Context.class);
    CustomerConversation c=new CustomerConversation(mock(CustomerStore.class),mock(CustomerMapper.class),new CustomerJson(new ObjectMapper()),model,mock(CustomerTools.class),mock(CustomerProjects.class),new CustomerProperties());
    CustomerConversation.Turn turn=c.newTurn(new CustomerRunContext("fit",Instant.now().plusSeconds(90),new ArrayList<>()));
    Map<String,Object> requirement;
    String quote="一个用于构建和部署内部工具、工作流和AI代理的平台，提供可视化构建器以及数据库和API集成。";
    CustomerRecommendationFitTest(){
        turn.state=new LinkedHashMap<>();CustomerWorkflow.initialize(turn.state,List.of(),"fit");turn.result=new LinkedHashMap<>();turn.context=context;
        requirement=CustomerRequirementService.entry(CustomerWorkflow.task(turn.state),"topic","能管理商品、采购、销售和库存",List.of("进销存","商品管理","采购管理","销售管理","库存管理"),"SOFT","m","能管理商品、采购、销售和库存","MESSAGE");
        CustomerWorkflow.task(turn.state).put("requirements",List.of(requirement));
        var ref=Map.<String,Object>of("evidenceId","E1","documentId",1,"versionId",1,"sourceRef","ToolJet/ToolJet","quote",quote);
        turn.cards=List.of(new LinkedHashMap<>(Map.of("type","EXTERNAL_PROJECT","id",329,"name","ToolJet/ToolJet","sourceRef","ToolJet/ToolJet","importable",true,"matchStatus","CONFIRMED","references",List.of(ref))));
        evidence(quote);
    }
    void evidence(String text){turn.evidence=List.of(Map.of("evidenceId","E1","documentId",1,"versionId",1,"sourceRef",turn.cards.getFirst().get("sourceRef"),"spans",List.of(Map.of("spanId","E1.S1","text",text))));}
    void propose(String verdict,String scope,boolean verified){
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenReturn(Map.of("matches",List.of(Map.of("projectKey","EXTERNAL_PROJECT:329","requirementId",requirement.get("id"),"verdict",verdict,"capabilityScope",scope,"evidenceId","E1","spanId","E1.S1","reason","fixture"))));
        when(model.call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList())).thenReturn(Map.of("supportedIds",verified?List.of(0):List.of()));
    }
    @Test void softBusinessPurposeIsSubmittedAndCannotBeVerifiedByVacuousHardCheck(){
        when(model.call(eq(CustomerRequirementMatcher.PROMPT),any(),any(),anyList())).thenAnswer(i->{
            var input=(Map<?,?>)i.getArgument(1);assertThat(CustomerWorkflow.rows(input.get("requirements")).getFirst()).containsEntry("dimension","PURPOSE");return Map.of("matches",List.of());
        });
        CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.result).containsEntry("status","PROJECTS_WITH_LIMITATIONS");assertThat(turn.cards.getFirst()).containsEntry("importable",false).containsEntry("matchStatus","NEEDS_VERIFICATION");
        assertThat(CustomerWorkflow.map(turn.cards.getFirst().get("purposeFit"))).containsEntry("status","UNKNOWN");assertThat(requirement).containsEntry("strength","SOFT");
    }
    @Test void capabilityToBuildIsNeverAcceptedAsExistingInventoryFunction(){
        propose("SATISFIED","ADAPTATION",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.cards.getFirst()).containsEntry("importable",false);assertThat(CustomerWorkflow.rows(turn.cards.getFirst().get("requirementMatches")).getFirst()).containsEntry("verdict","UNKNOWN");
        verify(model,never()).call(eq(CustomerRequirementMatcher.VERIFY_PROMPT),any(),any(),anyList());
    }
    @Test void explicitHardFeatureAlsoCannotTreatAdaptationAsSatisfied(){
        requirement.put("field","open");requirement.put("strength","HARD");propose("SATISFIED","ADAPTATION",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.cards.getFirst()).containsEntry("importable",false);assertThat(CustomerWorkflow.rows(turn.cards.getFirst().get("requirementMatches")).getFirst()).containsEntry("verdict","UNKNOWN");
    }
    @Test void independentVerificationCanRejectFirstModelsGenericBuilderInference(){
        propose("SATISFIED","EXISTING",false);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.result).containsEntry("status","PROJECTS_WITH_LIMITATIONS");assertThat(turn.cards.getFirst()).containsEntry("importable",false);
    }
    @Test void supportedExistingFunctionCanBeVerifiedWithoutPromotingUserStrength(){
        turn.cards.getFirst().put("name","fixture/inventory");turn.cards.getFirst().put("sourceRef","fixture/inventory");
        turn.cards.getFirst().put("references",List.of(Map.of("evidenceId","E1","documentId",1,"versionId",1,"sourceRef","fixture/inventory")));
        evidence("系统已实现商品档案、采购入库、销售出库及库存余额管理。");propose("SATISFIED","EXISTING",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.result).containsEntry("status","VERIFIED_PROJECTS");assertThat(CustomerWorkflow.map(turn.cards.getFirst().get("purposeFit"))).containsEntry("status","SATISFIED");
        assertThat(CustomerRecommendationPolicy.requiredSatisfied(turn.cards.getFirst(),turn.state)).isTrue();assertThat(requirement).containsEntry("strength","SOFT");
    }
    @Test void adaptationRemainsVisibleOnlyAsUnverifiedExploration(){
        propose("PARTIAL","ADAPTATION",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.cards).hasSize(1);assertThat(turn.cards.getFirst()).containsEntry("importable",false);assertThat(CustomerWorkflow.map(turn.cards.getFirst().get("purposeFit"))).containsEntry("status","PARTIAL");
    }
    @Test void strictModeRejectsPartiallyImplementedSoftBusinessPurpose(){
        turn.state.put("verificationMode","STRICT");propose("PARTIAL","ADAPTATION",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(turn.cards).isEmpty();assertThat(turn.result).containsEntry("status","NO_VERIFIED_MATCH");
    }
    @Test void confirmedPurposeConflictCannotBePresentedAsMatchingProject(){
        propose("CONFLICT","EXISTING",true);CustomerRequirementMatcher.match(c,turn);assertThat(turn.cards).isEmpty();
    }
    @Test void genericPracticeGoalIsNotAConcreteFunctionOrProofOfBeginnerSuitability(){
        requirement=CustomerRequirementService.entry(CustomerWorkflow.task(turn.state),"goal","练习","练习","SOFT","m","练习","MESSAGE");CustomerWorkflow.task(turn.state).put("requirements",List.of(requirement));
        CustomerRequirementMatcher.match(c,turn);verifyNoInteractions(model);assertThat(turn.result).containsEntry("status","PROJECTS_WITH_LIMITATIONS");assertThat(turn.cards.getFirst()).containsEntry("importable",false);
    }
    @Test void satisfiedPurposeDoesNotHideUnverifiedBeginnerScopeOfLargeRepository(){
        turn.state.put("experience","Java初学者");turn.cards.getFirst().put("difficulty","入门");turn.cards.getFirst().put("codebaseSize","LARGE");
        evidence("系统已实现商品档案、采购入库、销售出库及库存余额管理。");propose("SATISFIED","EXISTING",true);CustomerRequirementMatcher.match(c,turn);
        assertThat(CustomerWorkflow.map(turn.cards.getFirst().get("purposeFit"))).containsEntry("status","SATISFIED");
        assertThat(CustomerWorkflow.map(turn.cards.getFirst().get("learningFit"))).containsEntry("status","SCOPE_UNVERIFIED");
        assertThat(turn.result).containsEntry("status","PROJECTS_WITH_LIMITATIONS");
    }
}
