package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotaRequestIdentityTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void canonicalPayloadAndClientKeyProduceStableIdentity() {
        useKey("quota-key-1234567890");
        Map<String, Object> firstPayload = new LinkedHashMap<>();
        firstPayload.put("b", 2);
        firstPayload.put("a", 1);
        Map<String, Object> reorderedPayload = new LinkedHashMap<>();
        reorderedPayload.put("a", 1);
        reorderedPayload.put("b", 2);

        QuotaRequestIdentity first = QuotaRequestIdentity.current(
                "ai:plan", 7L, firstPayload);
        QuotaRequestIdentity replay = QuotaRequestIdentity.current(
                "ai:plan", 7L, reorderedPayload);

        assertEquals(first.requestId(), replay.requestId());
        assertEquals(first.requestHash(), replay.requestHash());
        assertEquals(first.idempotencyKeyHash(), replay.idempotencyKeyHash());
    }

    @Test
    void sameClientKeyWithChangedPayloadKeepsOperationIdentityButChangesRequestHash() {
        useKey("quota-key-1234567890");

        QuotaRequestIdentity first = QuotaRequestIdentity.current(
                "ai:plan", 7L, Map.of("summary", "A"));
        QuotaRequestIdentity changed = QuotaRequestIdentity.current(
                "ai:plan", 7L, Map.of("summary", "B"));

        assertEquals(first.requestId(), changed.requestId());
        assertNotEquals(first.requestHash(), changed.requestHash());
    }

    @Test
    void rejectsShortOrUnsafeClientKeys() {
        useKey("too-short");

        assertThrows(BaseException.class,
                () -> QuotaRequestIdentity.current("ai:plan", 7L, Map.of("a", 1)));
    }

    private void useKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", key);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
