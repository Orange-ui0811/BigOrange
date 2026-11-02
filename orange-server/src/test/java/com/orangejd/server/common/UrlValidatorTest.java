package com.orangejd.server.common;

import com.orangejd.common.util.UrlValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlValidatorTest {

    @Test
    void allowsKnownPublicHttpsHost() {
        assertTrue(UrlValidator.isSafe("https://api.github.com/repos/example/project"));
    }

    @Test
    void rejectsLocalNonHttpAndCredentialedUrls() {
        assertFalse(UrlValidator.isSafe("http://127.0.0.1/admin"));
        assertFalse(UrlValidator.isSafe("http://[::1]/admin"));
        assertFalse(UrlValidator.isSafe("file:///etc/passwd"));
        assertFalse(UrlValidator.isSafe("https://user:password@example.com/api"));
        assertFalse(UrlValidator.isSafe("https://example.com/api#secret"));
    }
}
