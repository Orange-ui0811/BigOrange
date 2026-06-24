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
        byte[] keyBytes = secretKey.getBytes();
        byte[] encoded = Base64.getEncoder().encode(keyBytes);
        byte[] decoded = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(decoded, "HmacSHA256");
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
