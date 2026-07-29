package com.orangejd.server.foundation.aitool;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolRouterTest {
    private AiToolRouter router;

    @AfterEach
    void tearDown() {
        if (router != null) router.shutdown();
    }

    @Test
    void disabledPoolPreservesLegacyConfigurationWithoutRedisLease() {
        AiToolPoolProperties properties = properties(false, 50, "");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        AiRuntimeConfigService configs = mock(AiRuntimeConfigService.class);
        AiToolCallLogMapper logs = mock(AiToolCallLogMapper.class);
        AiRuntimeConfig legacy = new AiRuntimeConfig("project", "legacy",
                "https://legacy.example/v1", "legacy-key", "legacy-model", "ENV");
        when(configs.current(AiRuntimeConfigService.PROJECT)).thenReturn(legacy);
        router = new AiToolRouter(properties, leases, configs, logs, new SimpleMeterRegistry());

        String result = router.execute(context(), AiRuntimeConfig::model);

        assertEquals("legacy-model", result);
        verify(leases, never()).acquireGlobal(any(), anyInt(), anyLong());
        verify(logs, never()).insert(any());
    }

    @Test
    void rejectsBeforeBusinessOperationWhenGlobalCapacityIsFull() {
        AiToolPoolProperties properties = properties(true, 50, "gitee-key-1");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        when(leases.acquireGlobal(any(), anyInt(), anyLong())).thenReturn(Optional.empty());
        router = new AiToolRouter(properties, leases, mock(AiRuntimeConfigService.class),
                mock(AiToolCallLogMapper.class), new SimpleMeterRegistry());
        AtomicBoolean invoked = new AtomicBoolean();

        assertThrows(BaseException.class, () -> router.execute(context(), config -> {
            invoked.set(true);
            return "unexpected";
        }));

        assertFalse(invoked.get());
    }

    @Test
    void usesExclusiveGiteeSlotAndAlwaysReleasesIt() {
        AiToolPoolProperties properties = properties(true, 50, "gitee-key-1");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        AiRuntimeConfigService configs = mock(AiRuntimeConfigService.class);
        AiToolCallLogMapper logs = mock(AiToolCallLogMapper.class);
        AiToolSlotLeaseService.GlobalLease global =
                new AiToolSlotLeaseService.GlobalLease("global-1", 330_000);
        AiToolPoolProperties.Slot slot = properties.slots().getFirst();
        AiToolSlotLeaseService.SlotLease slotLease =
                new AiToolSlotLeaseService.SlotLease("slot-1", "lease-value", slot, 330_000);
        when(leases.acquireGlobal(any(), anyInt(), anyLong())).thenReturn(Optional.of(global));
        when(leases.acquireSlot(any(), any(), anyLong())).thenReturn(Optional.of(slotLease));
        router = new AiToolRouter(properties, leases, configs, logs, new SimpleMeterRegistry());

        String result = router.execute(context(), AiRuntimeConfig::model);

        assertEquals("gitee-model", result);
        verify(configs, never()).current(AiRuntimeConfigService.TOOL_FALLBACK);
        verify(logs).finish("request-1", "SUCCEEDED", null, null, null);
        verify(leases).release(global, slotLease);
    }

    @Test
    void fallsBackToDeepSeekWhenEveryGiteeSlotIsBusy() {
        AiToolPoolProperties properties = properties(true, 50, "gitee-key-1");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        AiRuntimeConfigService configs = mock(AiRuntimeConfigService.class);
        AiToolCallLogMapper logs = mock(AiToolCallLogMapper.class);
        AiToolSlotLeaseService.GlobalLease global =
                new AiToolSlotLeaseService.GlobalLease("global-1", 330_000);
        AiRuntimeConfig fallback = new AiRuntimeConfig("tool_fallback", "v1",
                "https://fallback.example/v1", "fallback-key", "deepseek-model", "REDIS");
        when(leases.acquireGlobal(any(), anyInt(), anyLong())).thenReturn(Optional.of(global));
        when(leases.acquireSlot(any(), any(), anyLong())).thenReturn(Optional.empty());
        when(configs.current(AiRuntimeConfigService.TOOL_FALLBACK)).thenReturn(fallback);
        router = new AiToolRouter(properties, leases, configs, logs, new SimpleMeterRegistry());

        String result = router.execute(context(), AiRuntimeConfig::model);

        assertEquals("deepseek-model", result);
        verify(leases).release(global, null);
    }

    @Test
    void releasesLeasesWhenAuditLogInsertFails() {
        AiToolPoolProperties properties = properties(true, 50, "gitee-key-1");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        AiToolCallLogMapper logs = mock(AiToolCallLogMapper.class);
        AiToolSlotLeaseService.GlobalLease global =
                new AiToolSlotLeaseService.GlobalLease("global-1", 330_000);
        AiToolPoolProperties.Slot slot = properties.slots().getFirst();
        AiToolSlotLeaseService.SlotLease slotLease =
                new AiToolSlotLeaseService.SlotLease("slot-1", "lease-value", slot, 330_000);
        when(leases.acquireGlobal(any(), anyInt(), anyLong())).thenReturn(Optional.of(global));
        when(leases.acquireSlot(any(), any(), anyLong())).thenReturn(Optional.of(slotLease));
        doThrow(new IllegalStateException("database unavailable")).when(logs).insert(any());
        router = new AiToolRouter(properties, leases, mock(AiRuntimeConfigService.class),
                logs, new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class,
                () -> router.execute(context(), config -> "unexpected"));

        verify(leases).release(global, slotLease);
    }

    @Test
    void marksGiteeSlotInvalidAfterAuthenticationRejection() {
        RoutedFixture fixture = routedFixture();

        assertThrows(AiToolProviderException.class, () -> router.execute(context(), config -> {
            throw new AiToolProviderException(401, "AUTH_REJECTED", 0,
                    "AI 服务请求失败（HTTP 401）", null);
        }));

        verify(fixture.leases()).setInvalid(1);
        verify(fixture.logs()).finish("request-1", "FAILED", 401,
                "AUTH_REJECTED", "AI 服务请求失败（HTTP 401）");
        verify(fixture.leases()).release(fixture.global(), fixture.slotLease());
    }

    @Test
    void throttlesGiteeSlotUsingProviderRetryAfter() {
        RoutedFixture fixture = routedFixture();

        assertThrows(AiToolProviderException.class, () -> router.execute(context(), config -> {
            throw new AiToolProviderException(429, "RATE_LIMITED", 45_000,
                    "AI 服务请求失败（HTTP 429）", null);
        }));

        verify(fixture.leases()).setThrottled(1, 45_000);
        verify(fixture.logs()).finish("request-1", "FAILED", 429,
                "RATE_LIMITED", "AI 服务请求失败（HTTP 429）");
    }

    @Test
    void hardCapsGlobalConcurrencyAtEightyAndOnlyExposesFingerprint() {
        AiToolPoolProperties properties = properties(true, 999, "secret-key-value");

        assertEquals(80, properties.globalMaxConcurrency());
        assertEquals(1, properties.slots().size());
        assertEquals(12, properties.slots().getFirst().fingerprint().length());
        assertFalse(properties.slots().getFirst().fingerprint().contains("secret"));
        assertTrue(properties.poolReady());
    }

    @Test
    void parsesRetryAfterWithoutReadingProviderResponseBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "12");
        HttpClientErrorException http = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers,
                new byte[0], StandardCharsets.UTF_8);

        AiToolProviderException failure = AiToolProviderException.from(http);

        assertEquals(429, failure.httpStatus());
        assertEquals("RATE_LIMITED", failure.errorCode());
        assertEquals(12_000, failure.retryAfterMillis());
    }

    private AiToolContext context() {
        return new AiToolContext("plan.phase.generate", "request-1", 7L,
                "PLAN", "1", AiRuntimeConfigService.PROJECT);
    }

    private AiToolPoolProperties properties(boolean enabled, int max, String key1) {
        return new AiToolPoolProperties(enabled, max, 0, 330_000,
                "https://ai.example/v1", "gitee-model", key1,
                "", "", "", "", "", "", "", "", "");
    }

    private RoutedFixture routedFixture() {
        AiToolPoolProperties properties = properties(true, 50, "gitee-key-1");
        AiToolSlotLeaseService leases = mock(AiToolSlotLeaseService.class);
        AiToolCallLogMapper logs = mock(AiToolCallLogMapper.class);
        AiToolSlotLeaseService.GlobalLease global =
                new AiToolSlotLeaseService.GlobalLease("global-1", 330_000);
        AiToolPoolProperties.Slot slot = properties.slots().getFirst();
        AiToolSlotLeaseService.SlotLease slotLease =
                new AiToolSlotLeaseService.SlotLease("slot-1", "lease-value", slot, 330_000);
        when(leases.acquireGlobal(any(), anyInt(), anyLong())).thenReturn(Optional.of(global));
        when(leases.acquireSlot(any(), any(), anyLong())).thenReturn(Optional.of(slotLease));
        router = new AiToolRouter(properties, leases, mock(AiRuntimeConfigService.class),
                logs, new SimpleMeterRegistry());
        return new RoutedFixture(leases, logs, global, slotLease);
    }

    private record RoutedFixture(AiToolSlotLeaseService leases, AiToolCallLogMapper logs,
                                 AiToolSlotLeaseService.GlobalLease global,
                                 AiToolSlotLeaseService.SlotLease slotLease) {}
}
