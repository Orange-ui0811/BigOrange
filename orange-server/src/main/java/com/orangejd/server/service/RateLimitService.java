package com.orangejd.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Atomic fixed-window limiter shared by all application instances. */
@Service
public class RateLimitService {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",
            Long.class);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public boolean allow(String key, int limit, int windowSeconds) {
        if (redisTemplate == null) return true;
        Long count = redisTemplate.execute(INCREMENT_WITH_EXPIRY,
                Collections.singletonList("rate:" + digest(key)), String.valueOf(windowSeconds));
        return count != null && count <= limit;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
