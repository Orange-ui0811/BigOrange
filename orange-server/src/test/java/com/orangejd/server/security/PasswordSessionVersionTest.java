package com.orangejd.server.security;

import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.entity.Account;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordSessionVersionTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";

    @Test
    void acceptsMatchingAndLegacyVersionZeroTokens() {
        Account account = accountWithVersion(0L);
        Claims legacyClaims = claims(Map.of("id", 7L));
        Claims currentClaims = claims(Map.of("id", 7L, "passwordVersion", 0L));

        assertTrue(PasswordSessionVersion.matches(legacyClaims, account));
        assertTrue(PasswordSessionVersion.matches(currentClaims, account));
    }

    @Test
    void rejectsTokenIssuedBeforePasswordChange() {
        Account account = accountWithVersion(1L);
        Claims oldClaims = claims(Map.of("id", 7L, "passwordVersion", 0L));
        Claims newClaims = claims(Map.of("id", 7L, "passwordVersion", 1L));

        assertFalse(PasswordSessionVersion.matches(oldClaims, account));
        assertTrue(PasswordSessionVersion.matches(newClaims, account));
    }

    private Account accountWithVersion(Long version) {
        Account account = new Account();
        account.setPasswordVersion(version);
        return account;
    }

    private Claims claims(Map<String, Object> values) {
        String token = JwtUtil.createJwt(SECRET, 60_000, values);
        return JwtUtil.parseJwt(SECRET, token);
    }
}
