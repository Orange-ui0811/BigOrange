package com.orangejd.server.foundation.security;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalUrlPolicyTest {

    private final ExternalUrlPolicy policy = new ExternalUrlPolicy();

    @Test
    void allowsPublicHttpsWithoutResolvingIt() {
        assertTrue(policy.isSafe("https://github.com/example/project?tab=readme"));
        assertTrue(policy.isSafe("https://8.8.8.8/example"));
        assertEquals("https://example.com/demo", policy.requireSafeOptional("演示链接", " https://example.com/demo "));
    }

    @Test
    void rejectsUnsafeSchemesCredentialsAndPorts() {
        assertFalse(policy.isSafe("http://example.com"));
        assertFalse(policy.isSafe("javascript:alert(1)"));
        assertFalse(policy.isSafe("https://user:password@example.com"));
        assertFalse(policy.isSafe("https://example.com:8443"));
    }

    @Test
    void rejectsLocalPrivateAndObfuscatedHosts() {
        assertFalse(policy.isSafe("https://localhost/login"));
        assertFalse(policy.isSafe("https://service.local/login"));
        assertFalse(policy.isSafe("https://127.0.0.1/login"));
        assertFalse(policy.isSafe("https://10.0.0.1/login"));
        assertFalse(policy.isSafe("https://192.168.1.1/login"));
        assertFalse(policy.isSafe("https://198.51.100.9/login"));
        assertFalse(policy.isSafe("https://203.0.113.9/login"));
        assertFalse(policy.isSafe("https://[::1]/login"));
        assertFalse(policy.isSafe("https://[2001:db8::1]/login"));
        assertFalse(policy.isSafe("https://xn--example-9d0b.com/login"));
        assertFalse(policy.isSafe("https://2130706433/login"));
    }

    @Test
    void optionalBlankClearsTheFieldAndUnsafeValueRaisesExistingBusinessException() {
        assertEquals("", policy.requireSafeOptional("演示链接", "  "));
        assertThrows(BaseException.class, () -> policy.requireSafeOptional("演示链接", "http://example.com"));
    }
}
