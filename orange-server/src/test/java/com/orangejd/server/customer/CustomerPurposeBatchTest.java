package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reproduces the live 12-candidate / 10-item valid-empty batch without a provider. */
class CustomerPurposeBatchTest {
    final CustomerProperties config=new CustomerProperties();
    final CustomerModel model=mock(CustomerModel.class);
    final CustomerTools.Context context=mock(CustomerTools.Context.class);
    final CustomerStore store=mock(CustomerStore.class);
    final CustomerProjects projects=mock(CustomerProjects.class);
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerConversation c=new CustomerConversation(store,mock(CustomerMapper.class),json,model,mock(CustomerTools.class),projects,config);
    final List<Map<String,Object>> candidates=new ArrayList<>();
    final List<List<String>> sent=new ArrayList<>();
    final CustomerConversation.Turn t=turn(90);
    CustomerPurposeBatchTest() {
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","r");
        for(int i=1;i<=12;i++)candidates.add(Map.of("type","EXTERNAL_PROJECT","id",i,"sourceRef","fixture/"+i,"description","本项目实现小店商品采购销售和库存管理功能。".repeat(14)));
        var ten=CustomerPurpose.input(candidates.subList(0,10),t.state,json,6000);
        assertThat(CustomerWorkflow.rows(ten.get("candidates"))).hasSize(10);
        config.setInputTokens(CustomerModel.estimate(CustomerPurpose.REVIEW_PROMPT+json.write(ten))+CustomerPurpose.CORRECTION_RESERVE);
        assertThat(CustomerWorkflow.rows(CustomerPurpose.input(candidates,t.state,json,config.getInputTokens()).get("candidates"))).hasSize(10);
        when(context.purposeCandidates(anyMap(),anyList(),anyString())).thenReturn(candidates);
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),any(),any(),anyList())).thenAnswer(i->{record(i.getArgument(1));return Map.of("matches",List.of());});
    }
    CustomerConversation.Turn turn(int seconds) {
        var out=c.newTurn(new CustomerRunContext("r",Instant.now().plusSeconds(seconds),new ArrayList<>()));
        out.state=new LinkedHashMap<>(Map.of("topic",List.of("库存管理")));out.result=new LinkedHashMap<>();out.context=context;out.policy="fixture";out.purposeQuery="库存管理";return out;
    }
    void record(Map<String,Object> input) {sent.add(CustomerWorkflow.rows(input.get("candidates")).stream().map(p->p.get("key").toString()).toList());}
    @Test void validEmptyFirstBatchContinuesRemainingTwoAndCompletesBoundedNoMatch() {
        t.step("review_purpose");assertThat(t.next()).isEqualTo("select_projects");t.step(t.next());
        assertThat(sent).hasSize(2);assertThat(sent.getFirst()).hasSize(10);assertThat(sent.getLast()).containsExactly("EXTERNAL_PROJECT:11","EXTERNAL_PROJECT:12");
        assertThat(t.result).containsEntry("status","NO_MATCH");assertThat(CustomerPurpose.status(t.state)).isEqualTo("SUCCEEDED_EMPTY");
        assertThat((Map<String,Object>)t.result.get("purposeCoverage")).containsAllEntriesOf(Map.of("reviewedCount",12,"pendingCount",0,"batches",2));
        verify(context,never()).diagnose(anyMap());verifyNoInteractions(projects);
    }
    @Test void positiveOnlyInSecondBatchIsRetained() {
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),any(),any(),anyList())).thenAnswer(i->{
            Map<String,Object> input=i.getArgument(1);record(input);
            if(sent.size()==1)return Map.of("matches",List.of());
            var p=CustomerWorkflow.rows(input.get("candidates")).getFirst();
            return Map.of("matches",List.of(Map.of("key",p.get("key"),"relation","DIRECT","quote",p.get("purpose").toString().substring(0,20))));
        });
        t.step("review_purpose");assertThat(sent).hasSize(2);assertThat(CustomerPurpose.status(t.state)).isEqualTo("SUCCEEDED_MATCHES");
        assertThat(CustomerPurpose.matches(candidates.get(10),t.state)).isTrue();assertThat(CustomerPurpose.matches(candidates.getFirst(),t.state)).isFalse();
    }
    @Test void batchCapReturnsCoverageAndNextTurnOnlyChecksUnreviewedCandidates() {
        config.setPurposeMaxBatches(1);t.step("review_purpose");
        assertThat(t.next()).isEqualTo("finalize_response");assertThat(t.result).containsEntry("status","PURPOSE_REVIEW_PARTIAL");
        assertThat(CustomerOutcome.classify(t.result)).isEqualTo("COVERAGE_INCOMPLETE");
        assertThat(CustomerPurpose.status(t.state)).isEqualTo("PARTIAL_EMPTY");assertThat(t.state).doesNotContainKey("purposeFailure");
        var next=turn(90);next.state=t.state;next.step("review_purpose");
        assertThat(sent).hasSize(2);assertThat(sent.getLast()).containsExactly("EXTERNAL_PROJECT:11","EXTERNAL_PROJECT:12");
        assertThat(CustomerPurpose.status(next.state)).isEqualTo("SUCCEEDED_EMPTY");
    }
    @Test void insufficientTimeDoesNotStartAnotherProviderCallOrClaimFailure() {
        var shortTurn=turn(25);shortTurn.step("review_purpose");
        verifyNoInteractions(model);assertThat(shortTurn.result).containsEntry("status","PURPOSE_REVIEW_PARTIAL");
        assertThat((Map<String,Object>)shortTurn.result.get("purposeCoverage")).containsAllEntriesOf(Map.of("stopReason","TIME_BUDGET","reviewedCount",0));
    }
    @Test void invalidPartialOutputCannotResetRetryBudgetByStartingAnotherBatch() {
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),any(),any(),anyList())).thenAnswer(i->{
            Map<String,Object> input=i.getArgument(1);record(input);return Map.of("matches",List.of(),"unreviewedKeys",sent.getLast());
        });
        t.step("review_purpose");assertThat(sent).hasSize(1);assertThat(t.result).containsEntry("status","PURPOSE_REVIEW_PARTIAL");
        assertThat(CustomerPurposeMemory.pending(candidates,t.state,t.policy)).hasSize(12);
    }
    @Test void providerFailureIsStillFailureAndDoesNotAdvanceBatch() {
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),any(),any(),anyList())).thenThrow(new IllegalArgumentException("INVALID_MODEL_JSON"));
        t.step("review_purpose");assertThat(t.result).containsEntry("status","RELEVANCE_UNAVAILABLE");
        verify(model,times(1)).call(eq(CustomerPurpose.REVIEW_PROMPT),any(),any(),anyList());
        assertThat(CustomerPurposeMemory.pending(candidates,t.state,t.policy)).hasSize(12);
    }
}
