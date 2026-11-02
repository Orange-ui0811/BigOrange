package com.orangejd.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate redisTemplate;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Rule rule = ruleFor(request.getRequestURI(), request.getMethod());
        if (rule == null) return true;

        String client = clientAddress(request);
        String bucket = Long.toString(System.currentTimeMillis() / rule.window().toMillis());
        String key = "rate:" + rule.name() + ":" + client + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) redisTemplate.expire(key, rule.window().plusSeconds(5));
        if (count == null || count <= rule.limit()) return true;

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":0,\"msg\":\"请求过于频繁，请稍后重试\",\"data\":null}");
        return false;
    }

    private Rule ruleFor(String path, String method) {
        if (path.equals("/app/auth/login") || path.equals("/admin/auth/login")) {
            return new Rule("login", 10, Duration.ofMinutes(1));
        }
        if (path.contains("/auth/send-code") || path.contains("/auth/send-reset-code")) {
            return new Rule("verification", 5, Duration.ofMinutes(10));
        }
        if (path.equals("/app/auth/register")) {
            return new Rule("register", 5, Duration.ofHours(1));
        }
        if (path.startsWith("/app/common/upload")) {
            return new Rule("upload", 60, Duration.ofMinutes(1));
        }
        if (path.startsWith("/app/trend/") || path.startsWith("/app/plan/")
                || path.startsWith("/app/launch-check/") || path.startsWith("/app/job/match")) {
            return new Rule("ai", 20, Duration.ofMinutes(1));
        }
        if ("POST".equalsIgnoreCase(method)
                && (path.endsWith("/submit") || path.startsWith("/app/competition/submit"))) {
            return new Rule("submission", 20, Duration.ofDays(1));
        }
        if (path.startsWith("/agent/")) {
            return new Rule("agent", 120, Duration.ofMinutes(1));
        }
        return null;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return sanitize(forwarded.split(",")[0].trim());
        }
        return sanitize(request.getRemoteAddr());
    }

    private String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^0-9a-fA-F:._-]", "_");
    }

    private record Rule(String name, long limit, Duration window) {}
}
