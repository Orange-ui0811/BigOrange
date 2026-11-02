package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerModelTest {
    final ExternalAiClient client=mock(ExternalAiClient.class);
    final CustomerProperties config=new CustomerProperties();
    final CustomerModel model=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()));
    @Test void requirementPhasesDisableThinkingAndHaveIndependentFixedBudgets(){
        config.setThinking(true);
        when(client.callCustomer(eq(CustomerRequirementMatcher.PROMPT),anyString(),anyInt(),eq(2048),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[]}","test",Map.of(),1));
        when(client.callCustomer(eq(CustomerRequirementMatcher.VERIFY_PROMPT),anyString(),anyInt(),eq(1500),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"supportedIds\":[]}","test",Map.of(),1));
        var usage=new ArrayList<Map<String,Object>>();
        model.call(CustomerRequirementMatcher.PROMPT,Map.of(),Instant.now().plusSeconds(90),usage);
        model.call(CustomerRequirementMatcher.VERIFY_PROMPT,Map.of(),Instant.now().plusSeconds(90),usage);
        assertThat(usage.getFirst()).containsEntry("phase","REQUIREMENT_MATCH").containsEntry("thinking",false);
        assertThat((Integer)usage.getFirst().get("timeoutSeconds")).isLessThanOrEqualTo(20);
        assertThat(usage.getLast()).containsEntry("phase","REQUIREMENT_VERIFY").containsEntry("thinking",false);
        assertThat((Integer)usage.getLast().get("timeoutSeconds")).isLessThanOrEqualTo(12);
    }
    @Test void requirementTruncationRetriesOnceWithoutIncreasingOutputBudget(){
        when(client.callCustomer(anyString(),anyString(),anyInt(),eq(2048),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{","test",Map.of(),1,"length"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->model.call(CustomerRequirementMatcher.PROMPT,Map.of(),Instant.now().plusSeconds(90),usage)).hasMessage("MODEL_OUTPUT_TRUNCATED");
        assertThat(usage).hasSize(2).allMatch(row->row.get("outputBudget").equals(2048));
        verify(client,times(2)).callCustomer(anyString(),anyString(),anyInt(),eq(2048),eq(false));
    }
    @Test void malformedRequirementProtocolRetriesAtMostOnce(){
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"answer\":\"ok\"}","test",Map.of(),1));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->model.call(CustomerRequirementMatcher.VERIFY_PROMPT,Map.of(),Instant.now().plusSeconds(90),usage)).hasMessage("INVALID_REQUIREMENT_SCHEMA");
        assertThat(usage).hasSize(2);
    }
    @Test void requirementInputHasStricterBudgetThanGenericModel(){
        assertThatThrownBy(()->model.call(CustomerRequirementMatcher.PROMPT,Map.of("text","x".repeat(4000)),Instant.now().plusSeconds(90),new ArrayList<>())).hasMessage("CONTEXT_BUDGET_EXCEEDED");
        verifyNoInteractions(client);
    }
    @Test void observedTransportRecordsLateUsageOnceAndNeverRetriesAfterCancellation() {
        var ledger=new CustomerUsageLedger("run",mock(CustomerExecutionTrace.class));
        when(client.callCustomerObserved(anyString(),anyString(),anyInt(),anyInt(),anyBoolean(),nullable(Boolean.class),any()))
            .thenAnswer(i->{ledger.guard=()->{throw new IllegalStateException("RUN_STOPPED");};return new ExternalAiClient.TextResult("{\"intent\":\"CHAT\",\"reply\":\"迟到\"}","test",Map.of("total_tokens",17),20);});
        assertThatThrownBy(()->model.call(CustomerState.PROMPT,Map.of(),Instant.now().plusSeconds(90),ledger)).hasMessage("RUN_STOPPED");
        assertThat(ledger).hasSize(1);assertThat(ledger.getFirst()).containsEntry("usage",Map.of("total_tokens",17));
        verify(client,times(1)).callCustomerObserved(anyString(),anyString(),anyInt(),anyInt(),anyBoolean(),nullable(Boolean.class),any());
    }
    @Test void timeoutAndOutputLimitAreBoundedAndUsageRecorded() {
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt())).thenReturn(new ExternalAiClient.TextResult("{\"intent\":\"QUESTION\"}","test",Map.of("total_tokens",9),10));
        List<Map<String,Object>> usage=new ArrayList<>(); model.call("system",Map.of("message","hi"),Instant.now().plusSeconds(90),usage);
        verify(client).callCustomer(eq("system"),anyString(),eq(45),eq(1500)); assertThat(usage.getFirst()).containsEntry("costUsd","UNKNOWN");
    }
    @Test void authenticationErrorIsNotRetried() {
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt())).thenThrow(new AiToolProviderException(401,"AUTH_REJECTED",0,"invalid",new RuntimeException()));
        assertThatThrownBy(()->model.call("s",Map.of(),Instant.now().plusSeconds(90),new ArrayList<>()));
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt());
    }
    @Test void providerFailureHasOnlyOneRetryAndBothAttemptsCounted() {
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt())).thenThrow(new AiToolProviderException(503,"PROVIDER_UNAVAILABLE",0,"busy",new RuntimeException()));
        List<Map<String,Object>> usage=new ArrayList<>(); assertThatThrownBy(()->model.call("s",Map.of(),Instant.now().plusSeconds(90),usage));
        verify(client,times(2)).callCustomer(anyString(),anyString(),anyInt(),anyInt()); assertThat(usage).hasSize(2);
    }
    @Test void malformedNativeActionHasOneJsonFallbackAndBothCallsAreCounted() {
        config.setNativeActions(true);
        when(client.callCustomerAction(anyString(),anyString(),anyInt(),anyInt())).thenReturn(new ExternalAiClient.TextResult("invalid JSON","test",Map.of("total_tokens",12),10));
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt())).thenReturn(new ExternalAiClient.TextResult("{\"intent\":\"CHAT\",\"reply\":\"你好\"}","test",Map.of("total_tokens",14),10));
        List<Map<String,Object>> usage=new ArrayList<>();
        assertThat(model.call(CustomerState.PROMPT,Map.of(),Instant.now().plusSeconds(90),usage)).containsEntry("intent","CHAT");
        assertThat(usage).hasSize(2); assertThat(usage.getFirst()).containsEntry("status","FAILED");
        verify(client,times(1)).callCustomerAction(anyString(),anyString(),anyInt(),anyInt());
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt());
    }
    @Test void budgetAndDeadlinePreventOutboundCall() {
        assertThatThrownBy(()->model.call("x".repeat(6100),Map.of(),Instant.now().plusSeconds(90),new ArrayList<>())).hasMessage("CONTEXT_BUDGET_EXCEEDED");
        assertThatThrownBy(()->model.call("s",Map.of(),Instant.now(),new ArrayList<>())).hasMessage("DEADLINE_EXCEEDED"); verifyNoInteractions(client);
    }
    @Test void invalidPaidResponseRetainsUsageWithoutDoubleCounting() {
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt())).thenReturn(new ExternalAiClient.TextResult("not json","test",Map.of("total_tokens",12),10));
        List<Map<String,Object>> usage=new ArrayList<>(); assertThatThrownBy(()->model.call("s",Map.of(),Instant.now().plusSeconds(90),usage));
        assertThat(usage).hasSize(1); assertThat(usage.getFirst()).containsEntry("status","FAILED").containsEntry("usage",Map.of("total_tokens",12));
    }
    @Test void truncatedEvidenceUsesOneLargerRetryAndRecordsActualFinishReason() {
        config.setEvidenceOutputTokens(6144);
        config.setEvidenceThinking(true);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(true)))
            .thenReturn(new ExternalAiClient.TextResult("{", "served-model",Map.of("completion_tokens",6144),10,"length"))
            .thenReturn(new ExternalAiClient.TextResult("{\"claims\":[]}", "served-model",Map.of("completion_tokens",20),10,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThat(model.call(CustomerEvidence.PROMPT,Map.of(),Instant.now().plusSeconds(90),usage)).containsKey("claims");
        verify(client).callCustomer(anyString(),anyString(),anyInt(),eq(6144),eq(true));
        verify(client).callCustomer(anyString(),anyString(),anyInt(),eq(8192),eq(true));
        assertThat(usage).hasSize(2);assertThat(usage.getFirst()).containsEntry("errorCode","MODEL_OUTPUT_TRUNCATED").containsEntry("finishReason","length").containsEntry("phase","EVIDENCE").containsEntry("model","served-model");
    }
    @Test void evidenceUsesIndependentThinkingPolicyWithoutChangingChat() {
        config.setThinking(true);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"claims\":[]}","served",Map.of(),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        model.call(CustomerEvidence.PROMPT,Map.of(),Instant.now().plusSeconds(90),usage);
        verify(client).callCustomer(eq(CustomerEvidence.PROMPT),anyString(),anyInt(),eq(2048),eq(false));
        assertThat(config.isThinking()).isTrue(); assertThat(usage.getFirst()).containsEntry("thinking",false);
    }
    @Test void providerFailureRecordsSafeStatusWithoutResponseBodyAndDoesNotRetry400() {
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenThrow(new AiToolProviderException(400,"HTTP_400",0,"secret response content",null));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->model.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),Instant.now().plusSeconds(90),usage));
        assertThat(usage).hasSize(1);
        assertThat(usage.getFirst()).containsEntry("httpStatus",400).containsEntry("errorCode","PROVIDER_HTTP_400").containsEntry("retryable",false);
        assertThat(usage.toString()).doesNotContain("secret response content");
        assertThat(usage.getFirst()).containsEntry("thinking",false).containsEntry("outputBudget",2048).containsEntry("retryDecision","NOT_RETRYABLE");
        assertThat((Integer)usage.getFirst().get("timeoutSeconds")).isBetween(19,20);
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false));
    }
    static class TestClock extends java.time.Clock {
        Instant now=Instant.parse("2026-10-08T00:00:00Z");
        public java.time.ZoneId getZone() {return java.time.ZoneOffset.UTC;}
        public java.time.Clock withZone(java.time.ZoneId zone) {return this;}
        public Instant instant() {return now;}
        void advance(int seconds) {now=now.plusSeconds(seconds);}
    }
    @Test void purposeUsesIndependentOutputThinkingAndFirstAttemptReservation() {
        config.setThinking(true);config.setActionOutputTokens(8192);
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[]}","served",Map.of(),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),clock.instant().plusSeconds(90),usage);
        verify(client).callCustomer(eq(CustomerPurpose.REVIEW_PROMPT),anyString(),eq(20),eq(2048),eq(false));
        assertThat(config.isThinking()).isTrue();assertThat(usage.getFirst()).containsEntry("timeoutSeconds",20);
    }
    @Test void timedOutPurposeFirstAttemptLeavesOnlySharedRemainingTimeForRetry() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenAnswer(i->{clock.advance(20);throw new org.springframework.web.client.ResourceAccessException("timeout");})
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[]}","served",Map.of("total_tokens",7),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),clock.instant().plusSeconds(90),usage);
        verify(client).callCustomer(anyString(),anyString(),eq(20),eq(2048),eq(false));
        verify(client).callCustomer(anyString(),anyString(),eq(10),eq(2048),eq(false));
        assertThat(usage).hasSize(2);assertThat(usage.getFirst()).containsEntry("retryDecision","RETRY").containsEntry("remainingSeconds",10L);
    }
    @Test void purposeDoesNotRetryWithOnlySevenSecondsLeft() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenAnswer(i->{clock.advance(23);throw new org.springframework.web.client.ResourceAccessException("late timeout");});
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),clock.instant().plusSeconds(90),usage));
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false));
        assertThat(usage.getFirst()).containsEntry("retryDecision","INSUFFICIENT_TIME");
    }
    @Test void truncatedPurposeThinkingHasOneNonThinkingFallbackWithIdenticalInput() {
        config.setPurposeThinking(true);
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(true)))
            .thenAnswer(i->{clock.advance(15);return new ExternalAiClient.TextResult("","served",Map.of("completion_tokens",2048),15000,"length");});
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[]}","served",Map.of("completion_tokens",9),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of("topic","RAG"),clock.instant().plusSeconds(90),usage);
        verify(client).callCustomer(eq(CustomerPurpose.REVIEW_PROMPT),eq("{\"topic\":\"RAG\"}"),eq(20),eq(2048),eq(true));
        verify(client).callCustomer(eq(CustomerPurpose.REVIEW_PROMPT),eq("{\"topic\":\"RAG\"}"),eq(15),eq(2048),eq(false));
        assertThat(usage).hasSize(2);assertThat(usage.getFirst()).containsEntry("usage",Map.of("completion_tokens",2048)).containsEntry("errorCode","MODEL_OUTPUT_TRUNCATED");
    }
    @Test void nonThinkingPurposeTruncationHasOneLargerBoundedRetry() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenAnswer(i->{clock.advance(5);return new ExternalAiClient.TextResult("{","served",Map.of(),1,"length");})
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[]}","served",Map.of(),1,"stop"));
        controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),clock.instant().plusSeconds(90),new ArrayList<>());
        verify(client).callCustomer(anyString(),anyString(),eq(20),eq(2048),eq(false));
        verify(client).callCustomer(anyString(),anyString(),eq(25),eq(4096),eq(false));
    }
    @Test void shortRunDeadlineDoesNotCreateANewPurposeStageBudget() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenAnswer(i->{clock.advance(9);throw new org.springframework.web.client.ResourceAccessException("timeout");});
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),clock.instant().plusSeconds(12),usage));
        verify(client).callCustomer(anyString(),anyString(),eq(12),eq(2048),eq(false));
        assertThat(usage.getFirst()).containsEntry("remainingSeconds",3L).containsEntry("retryDecision","INSUFFICIENT_TIME");
    }
    @Test void purposeConfigurationIsBoundedAndChangesCacheIdentity() {
        String before=model.policyIdentity();config.setPurposeThinking(true);
        assertThat(model.policyIdentity()).isNotEqualTo(before);
        assertThatThrownBy(()->config.setPurposeOutputTokens(9000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->config.setPurposeSeconds(45)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->config.setPurposeRetryReserveSeconds(1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void purposeInvalidIdentityAndQuoteAreRetriedInsideTheSameAttemptLimit() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        String text="This project provides retrieval augmented generation for local documents.";
        var input=Map.of("topic","RAG","candidates",List.of(Map.of("key","CAREER_PROJECT:215","purpose",text)));
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenAnswer(i->{clock.advance(2);return new ExternalAiClient.TextResult("{\"matches\":[{\"key\":\"EXTERNAL_PROJECT:215\",\"relation\":\"DIRECT\",\"quote\":\"fictional purpose\"}]}","served",Map.of("total_tokens",50),1,"stop");})
            .thenReturn(new ExternalAiClient.TextResult(new CustomerJson(new ObjectMapper()).write(Map.of("matches",List.of(Map.of("key","CAREER_PROJECT:215","relation","DIRECT","quote",text)))),"served",Map.of("total_tokens",55),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThat(controlled.call(CustomerPurpose.REVIEW_PROMPT,input,clock.instant().plusSeconds(90),usage)).containsKey("matches");
        var requests=org.mockito.ArgumentCaptor.forClass(String.class);
        var prompts=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client,times(2)).callCustomer(prompts.capture(),requests.capture(),anyInt(),eq(2048),eq(false));
        assertThat(requests.getAllValues().get(1)).isEqualTo(requests.getAllValues().get(0));
        assertThat(prompts.getAllValues().get(0)).isEqualTo(CustomerPurpose.REVIEW_PROMPT);
        assertThat(prompts.getAllValues().get(1)).contains("服务端校验反馈","USE_EXACT_SUPPLIED_KEY");
        assertThat(usage).hasSize(2);assertThat(usage.getFirst()).containsEntry("status","FAILED").containsEntry("errorCode","INVALID_PURPOSE_REVIEW").containsEntry("retryDecision","RETRY").containsEntry("usage",Map.of("total_tokens",50));
    }
    @Test void purposeRejectsOverlongAndInventedQuotesAndMissingRelation() {
        var input=Map.<String,Object>of("candidates",List.of(Map.of("key","EXTERNAL_PROJECT:1","purpose","x".repeat(200))));
        assertThat(CustomerPurpose.outputErrors(Map.of("matches",List.of(Map.of("key","EXTERNAL_PROJECT:1","relation","DIRECT","quote","x".repeat(200)))),input))
            .containsExactly("matches[0]:QUOTE_LENGTH_12_TO_180_REQUIRED");
        assertThat(CustomerPurpose.outputErrors(Map.of("matches",List.of(Map.of("key","EXTERNAL_PROJECT:1","quote","unsupported quote"))),input))
            .contains("matches[0]:RELATION_DIRECT_OR_SUBTOPIC_REQUIRED","matches[0]:QUOTE_MUST_BE_CONTIGUOUS_SUPPLIED_TEXT");
    }
    @Test void invalidPurposeAfterRetryIsNeverAcceptedOrRepairedLocally() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{\"matches\":[{\"key\":\"FAKE:1\"}]}","served",Map.of("total_tokens",5),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->controlled.call(CustomerPurpose.REVIEW_PROMPT,Map.of("candidates",List.of()),clock.instant().plusSeconds(90),usage)).hasMessage("INVALID_PURPOSE_REVIEW");
        verify(client,times(2)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false));
        assertThat(usage).hasSize(2);assertThat(usage).allMatch(c->"FAILED".equals(c.get("status")));
        assertThat(usage.getLast()).containsEntry("retryDecision","ATTEMPTS_EXHAUSTED");
    }
    @Test void purposeCorrectionCannotExceedTheExistingInputBudget() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        Map<String,Object> input=new LinkedHashMap<>(Map.of("candidates",List.of(),"padding",""));
        int overhead=CustomerModel.estimate(CustomerPurpose.REVIEW_PROMPT+new CustomerJson(new ObjectMapper()).write(input));
        input.put("padding","x".repeat(config.getInputTokens()-overhead));
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult("{}","served",Map.of("total_tokens",5),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->controlled.call(CustomerPurpose.REVIEW_PROMPT,input,clock.instant().plusSeconds(90),usage)).hasMessage("INVALID_PURPOSE_REVIEW");
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false));
        assertThat(usage.getFirst()).containsEntry("retryDecision","CONTEXT_BUDGET_EXCEEDED");
    }
    @Test void budgetedPurposeInputAlwaysLeavesSpaceForOneCorrectionWithIdenticalCandidates() {
        var json=new CustomerJson(new ObjectMapper());
        List<Map<String,Object>> cards=new ArrayList<>();
        for(int i=1;i<=12;i++)cards.add(Map.of("id",i,"type","EXTERNAL_PROJECT","description","A project for retrieval augmented generation. ".repeat(20)));
        var input=CustomerPurpose.input(cards,Map.of("topic",List.of("RAG")),json,config.getInputTokens());
        String corrected=CustomerPurpose.correctionPrompt(Collections.nCopies(24,"matches[11]:QUOTE_MUST_BE_CONTIGUOUS_SUPPLIED_TEXT"));
        assertThat(CustomerModel.estimate(corrected+json.write(input))).isLessThanOrEqualTo(config.getInputTokens());
        assertThat(CustomerModel.estimate(corrected)-CustomerModel.estimate(CustomerPurpose.REVIEW_PROMPT)).isLessThanOrEqualTo(CustomerPurpose.CORRECTION_RESERVE);
        var candidate=(Map<?,?>)((List<?>)input.get("candidates")).getFirst();
        String key=candidate.get("key").toString();
        String invalid=json.write(Map.of("matches",List.of(Map.of("key",key,"relation","DIRECT","quote","A project for retrieval augmented generation. ".repeat(5)))));
        String valid=json.write(Map.of("matches",List.of(Map.of("key",key,"relation","DIRECT","quote","retrieval augmented generation"))));
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false)))
            .thenReturn(new ExternalAiClient.TextResult(invalid,"served",Map.of("total_tokens",5),1,"stop"))
            .thenReturn(new ExternalAiClient.TextResult(valid,"served",Map.of("total_tokens",6),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        assertThat(model.call(CustomerPurpose.REVIEW_PROMPT,input,Instant.now().plusSeconds(90),usage)).containsKey("matches");
        verify(client,times(2)).callCustomer(anyString(),eq(json.write(input)),anyInt(),eq(2048),eq(false));
        assertThat(usage).hasSize(2);assertThat(usage.getFirst()).containsEntry("retryDecision","RETRY");
        assertThat(usage.getLast()).containsEntry("status","SUCCEEDED");
    }
    @Test void secondPurposeAttemptCanRetainAValidatedSubsetWithoutAnExtraCall() {
        var clock=new TestClock();var controlled=new CustomerModel(client,config,new CustomerJson(new ObjectMapper()),clock);
        var input=Map.of("candidates",List.of(Map.of("key","EXTERNAL_PROJECT:1","purpose","A project for retrieval augmented generation."),Map.of("key","EXTERNAL_PROJECT:2","purpose","x".repeat(200))));
        String response=new CustomerJson(new ObjectMapper()).write(Map.of("matches",List.of(
            Map.of("key","EXTERNAL_PROJECT:1","relation","DIRECT","quote","retrieval augmented generation"),
            Map.of("key","EXTERNAL_PROJECT:2","relation","DIRECT","quote","x".repeat(200)))));
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false))).thenReturn(new ExternalAiClient.TextResult(response,"served",Map.of("total_tokens",25),1,"stop"));
        var usage=new ArrayList<Map<String,Object>>();
        var output=controlled.call(CustomerPurpose.REVIEW_PROMPT,input,clock.instant().plusSeconds(90),usage);
        assertThat((List<?>)output.get("matches")).hasSize(1);
        assertThat(output.get("unreviewedKeys")).isEqualTo(List.of("EXTERNAL_PROJECT:2"));
        assertThat(usage).hasSize(2);assertThat(usage.getLast()).containsEntry("status","SUCCEEDED").containsEntry("outputValidation","PARTIAL_VALIDATED");
        verify(client,times(2)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(false));
    }
}
