package com.orangejd.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@lombok.RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    private final @org.springframework.lang.Nullable StringRedisTemplate redisTemplate;

    private final @org.springframework.lang.Nullable org.springframework.ai.vectorstore.VectorStore vectorStore;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        return result;
    }

    @GetMapping("/health/detail")
    public Map<String, Object> healthDetail() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db", checkDb());
        result.put("redis", checkRedis());
        result.put("rag_vector_store", vectorStore != null ? "UP" : "NOT_CONFIGURED");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private String checkDb() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        if (redisTemplate == null) return "NOT_CONFIGURED";
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            return "PONG".equals(connection.ping()) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
