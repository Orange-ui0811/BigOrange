package com.orangejd.server.service;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAuditServiceTest {

    @Test
    void connectionTestLeavesEnoughOutputBudgetForReasoningModels() {
        AiRuntimeConfigService configService = mock(AiRuntimeConfigService.class);
        ExternalAiClient aiClient = mock(ExternalAiClient.class);
        AiRuntimeConfig config = new AiRuntimeConfig(
                AiRuntimeConfigService.AUDIT,
                "test-version",
                "https://example.com/v1",
                "secret",
                "reasoning-model",
                "TEST");
        when(configService.current(AiRuntimeConfigService.AUDIT)).thenReturn(config);
        when(aiClient.call(eq(config), any(Map.class), eq("content-audit"))).thenReturn("OK");

        AiAuditService service = new AiAuditService();
        ReflectionTestUtils.setField(service, "aiConfigService", configService);
        ReflectionTestUtils.setField(service, "externalAiClient", aiClient);
        ReflectionTestUtils.setField(service, "model", "reasoning-model");

        assertEquals("OK", service.testConnection());
        verify(aiClient).call(eq(config),
                org.mockito.ArgumentMatchers.argThat(body ->
                        Integer.valueOf(AiAuditService.CONNECTION_TEST_MAX_TOKENS)
                                .equals(body.get("max_tokens"))),
                eq("content-audit"));
    }
}
