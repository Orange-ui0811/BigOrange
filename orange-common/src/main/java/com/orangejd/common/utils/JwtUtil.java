package com.orangejd.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public final class JwtUtil {
    private JwtUtil() {}

    private static SecretKey toKey(String secretKey) {
        // 确保密钥长度 ≥ 256 bit (32 bytes) for HS256
        byte[] keyBytes = secretKey.getBytes();
        if (keyBytes.length < 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public static String createJwt(String secretKey, long ttlMillis, Map<String, Object> claims) {
        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + ttlMillis))
                .signWith(toKey(secretKey))
                .compact();
    }

    public static Claims parseJwt(String secretKey, String token) {
        return Jwts.parser()
                .verifyWith(toKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
