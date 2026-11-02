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
import org.mockito.ArgumentCaptor;

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
        when(quotaService.consume(eq(7L), eq(PlanGenerationService.PRICING_KEY),
                anyString(), any(QuotaRequestIdentity.class)))
                .thenReturn(new QuotaCharge(60, 40, 1, "operation-1", false));
    }

    @Test
    void generatesUsingSharedQuotaRules() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "可执行方案"))))));

        Map<String, Object> result = service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台"));

        assertEquals("可执行方案", result.get("content"));
        assertEquals(40L, result.get("segmentCost"));
        assertEquals(60L, result.get("balance"));
    }

    @Test
    void appendsConfirmedPersonaAsBoundedBackgroundContext() {
        PersonaPromptContextService contextService = mock(PersonaPromptContextService.class);
        when(contextService.confirmedContext(7L)).thenReturn(
                "## 账号中已由用户确认的长期画像\n- Build an Agent project");
        ReflectionTestUtils.setField(service, "personaPromptContextService", contextService);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "可执行方案"))))));

        service.generate(7L, Map.of("phase", 1, "summary", "设计协作平台"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), request.capture(), eq(Map.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) request.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        assertEquals(true, messages.get(1).get("content").contains("Build an Agent project"));
    }

    @Test
    void refundsWhenModelReturnsNoResult() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of())));

        assertThrows(RuntimeException.class, () -> service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台")));

        verify(quotaService).refund(eq(7L), anyString(), anyString());
    }

    @Test
    void capacityRejectionHappensBeforeQuotaConsumption() {
        AiToolRouter router = mock(AiToolRouter.class);
        ReflectionTestUtils.setField(service, "aiToolRouter", router);
        when(router.execute(any(), any())).thenThrow(new BaseException("AI 服务繁忙，请稍后重试"));

        assertThrows(BaseException.class, () -> service.generate(7L, Map.of(
                "phase", 1, "summary", "设计一个面向开发者的协作平台")));

        verify(quotaService, never()).consume(any(), anyString(), anyString(),
                any(QuotaRequestIdentity.class));
    }

    @Test
    void marksTheProductionConstructorForSpringInjection() {
        long annotated = Arrays.stream(PlanGenerationService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1L, annotated);
    }
}
