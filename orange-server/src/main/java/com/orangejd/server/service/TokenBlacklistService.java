package com.orangejd.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单：退出登录后将 token 加入 Redis，TTL 后自动过期
 */
@Service
public class TokenBlacklistService {

    private static final String PREFIX = "blacklist:token:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public void blacklist(String token, long ttlMillis) {
        if (redisTemplate == null) return;
        redisTemplate.opsForValue().set(PREFIX + token, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        if (redisTemplate == null) return false; // Redis 不可用降级放行
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
