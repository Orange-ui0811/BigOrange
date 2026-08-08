package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobMatchServiceTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void parsesValidatedMatchesForClientRendering() throws Exception {
        JobMatchService service = new JobMatchService(new ObjectMapper(), mock(RestTemplate.class));

        List<Map<String, String>> matches = service.parseMatches(
                "{\"matches\":[{\"position\":\"Java开发\",\"reason\":\"技术匹配\",\"focus\":\"并发编程\"}]}"
        );

        assertEquals("Java开发", matches.get(0).get("position"));
        assertEquals("技术匹配", matches.get(0).get("reason"));
    }

    @Test
    void malformedAiResponseRefundsConsumedQuota() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        QuotaServiceImpl quotaService = mock(QuotaServiceImpl.class);
        PricingService pricingService = mock(PricingService.class);
        JobMatchService service = new JobMatchService(new ObjectMapper(), restTemplate);
        ReflectionTestUtils.setField(service, "quotaService", quotaService);
        ReflectionTestUtils.setField(service, "pricingService", pricingService);
        ReflectionTestUtils.setField(service, "endpoint", "https://ai.example.com");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");

        when(pricingService.getCost("ai:match")).thenReturn(50);
        when(quotaService.consume(eq(7L), eq("ai:match"), eq("岗位匹配"),
                any(QuotaRequestIdentity.class)))
                .thenReturn(new QuotaCharge(950, 50, 1, "operation-1", false));
        Map<String, Object> message = Map.of("content", "not-json");
        Map<String, Object> response = Map.of("choices", List.of(Map.of("message", message)));
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));
        BaseContext.setCurrentId(7L);

        assertThrows(RuntimeException.class, () -> service.match("测试方案"));

        verify(quotaService).consume(eq(7L), eq("ai:match"), eq("岗位匹配"),
                any(QuotaRequestIdentity.class));
        verify(quotaService).refund(eq(7L), anyString(), eq("岗位匹配失败退款"));
    }
}
