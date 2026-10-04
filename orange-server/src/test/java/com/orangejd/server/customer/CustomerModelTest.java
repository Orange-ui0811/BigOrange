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
        when(client.callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(true)))
            .thenThrow(new AiToolProviderException(400,"HTTP_400",0,"secret response content",null));
        var usage=new ArrayList<Map<String,Object>>();
        assertThatThrownBy(()->model.call(CustomerPurpose.REVIEW_PROMPT,Map.of(),Instant.now().plusSeconds(90),usage));
        assertThat(usage).hasSize(1);
        assertThat(usage.getFirst()).containsEntry("httpStatus",400).containsEntry("errorCode","PROVIDER_HTTP_400").containsEntry("retryable",false);
        assertThat(usage.toString()).doesNotContain("secret response content");
        verify(client,times(1)).callCustomer(anyString(),anyString(),anyInt(),anyInt(),eq(true));
    }
}
