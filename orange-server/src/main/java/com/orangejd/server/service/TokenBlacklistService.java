package com.orangejd.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单：退出登录后将 token 加入 Redis，TTL 后自动过期
 */
@Service
@lombok.RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "blacklist:token:";

    private final @org.springframework.lang.Nullable StringRedisTemplate redisTemplate;

    public void blacklist(String token, long ttlMillis) {
        if (redisTemplate == null) return;
        redisTemplate.opsForValue().set(key(token), "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        if (redisTemplate == null) return false; // Redis 不可用降级放行
        // Also check the legacy raw-token key until previously issued tokens expire.
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)))
                || Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }

    private String key(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
