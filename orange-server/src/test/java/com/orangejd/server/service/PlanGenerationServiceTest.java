package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiToolRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PlanGenerationServiceTest {
    private QuotaService quotaService;
    private PricingService pricingService;
    private RestTemplate restTemplate;
    private PlanGenerationService service;

    @BeforeEach
    void setUp() {
        quotaService = mock(QuotaService.class);
        pricingService = mock(PricingService.class);
        restTemplate = mock(RestTemplate.class);
        service = new PlanGenerationService(quotaService, pricingService, restTemplate);
        ReflectionTestUtils.setField(service, "apiEndpoint", "https://ai.example.com/v1");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        when(pricingService.getCost(PlanGenerationService.PRICING_KEY)).thenReturn(40);
        when(quotaService.getBalance(7L)).thenReturn(100L);
        when(quotaService.consume(eq(7L), eq(40), anyString(), anyString())).thenReturn(60);
    }

    @Test
    void generatesUsingSharedQuotaRules() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "可执行方案"))))));

        Map<String, Object> result = service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台"));

        assertEquals("可执行方案", result.get("content"));
        assertEquals(40, result.get("segmentCost"));
        assertEquals(60L, result.get("balance"));
    }

    @Test
    void refundsWhenModelReturnsNoResult() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of())));

        assertThrows(RuntimeException.class, () -> service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台")));

        verify(quotaService).refund(eq(7L), eq(40), anyString(), anyString());
    }

    @Test
    void capacityRejectionHappensBeforeQuotaConsumption() {
        AiToolRouter router = mock(AiToolRouter.class);
        ReflectionTestUtils.setField(service, "aiToolRouter", router);
        when(router.execute(any(), any())).thenThrow(new BaseException("AI 服务繁忙，请稍后重试"));

        assertThrows(BaseException.class, () -> service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台")));

        verify(quotaService, never()).consume(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void marksTheProductionConstructorForSpringInjection() {
        long annotated = Arrays.stream(PlanGenerationService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1L, annotated);
    }
}
