package com.orangejd.server.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/** Issues short-lived, single-use credentials so JWTs never need to appear in WebSocket URLs. */
@Service
public class WebSocketTicketService {
    private static final String PREFIX = "ws:ticket:";
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v==ARGV[1] then redis.call('DEL',KEYS[1]); return 1; end; return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public WebSocketTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(Long accountId) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREFIX + ticket, accountId.toString(), Duration.ofSeconds(30));
        return ticket;
    }

    public boolean consume(String ticket, String accountId) {
        if (ticket == null || ticket.isBlank()) return false;
        Long result = redisTemplate.execute(CONSUME,
                Collections.singletonList(PREFIX + ticket), accountId);
        return result != null && result == 1L;
    }
}
