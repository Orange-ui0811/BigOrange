package com.orangejd.server.service;

import com.orangejd.server.mapper.AgentDeviceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDeviceAuthServiceTest {

    @Test
    void exchangesOneTimeCodeForHashedScopedToken() {
        AgentDeviceMapper mapper = mock(AgentDeviceMapper.class);
        AgentDeviceAuthService service = new AgentDeviceAuthService(mapper);
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("id", 3L);
        link.put("accountId", 7L);
        link.put("deviceName", "StackChan");
        link.put("expiresAt", LocalDateTime.now().plusMinutes(5));
        when(mapper.selectUsableLinkCode(any())).thenReturn(link);
        when(mapper.consumeLinkCode(3L)).thenReturn(1);
        doAnswer(invocation -> {
            Map<String, Object> record = invocation.getArgument(0);
            record.put("id", 9L);
            return 1;
        }).when(mapper).insertCredential(any());

        Map<String, Object> result = service.exchange("ABCD-EFGH", "My StackChan");

        assertEquals(9L, result.get("deviceId"));
        assertTrue(String.valueOf(result.get("token")).startsWith("ojd_"));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertCredential(captor.capture());
        Map<String, Object> stored = captor.getValue();
        assertEquals(64, String.valueOf(stored.get("tokenHash")).length());
        assertFalse(String.valueOf(stored.get("tokenHash")).contains("ojd_"));
        assertTrue(String.valueOf(stored.get("scopes")).contains("quota.balance.read"));
        assertTrue(String.valueOf(stored.get("scopes")).contains("agent.task.list"));
        assertTrue(String.valueOf(stored.get("scopes")).contains("notification.unread.read"));
        assertTrue(String.valueOf(stored.get("scopes")).contains("leaderboard.github.list"));
        assertFalse(String.valueOf(stored.get("scopes")).contains("admin"));
    }
}
