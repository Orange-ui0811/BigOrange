package com.orangejd.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public final class JwtUtil {
    private JwtUtil() {}

    private static SecretKey toKey(String secretKey) {
        // 确保密钥长度 ≥ 256 bit (32 bytes) for HS256
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private static final String TOKEN_PREFIX = "sk-";

    public static String createJwt(String secretKey, long ttlMillis, Map<String, Object> claims) {
        long nowMillis = System.currentTimeMillis();
        return TOKEN_PREFIX + Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + ttlMillis))
                .signWith(toKey(secretKey))
                .compact();
    }

    public static Claims parseJwt(String secretKey, String token) {
        // 兼容新旧格式
        if (token.startsWith(TOKEN_PREFIX)) token = token.substring(TOKEN_PREFIX.length());
        return Jwts.parser()
                .verifyWith(toKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
