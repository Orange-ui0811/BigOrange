package com.orangejd.server.foundation.aitool;

import com.orangejd.common.exception.BaseException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class AiToolProviderException extends BaseException {
    private final int httpStatus;
    private final String errorCode;
    private final long retryAfterMillis;

    public AiToolProviderException(int httpStatus, String errorCode, long retryAfterMillis,
                                   String message, Throwable cause) {
        super(message);
        initCause(cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }
    public long retryAfterMillis() { return retryAfterMillis; }

    public static AiToolProviderException from(Throwable throwable) {
        AiToolProviderException existing = find(throwable);
        if (existing != null) return existing;
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException http) {
                int status = http.getStatusCode().value();
                return new AiToolProviderException(status, errorCode(status),
                        retryAfterMillis(http.getResponseHeaders()),
                        "AI 服务请求失败（HTTP " + status + "）", http);
            }
        }
        return null;
    }

    public static AiToolProviderException find(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof AiToolProviderException provider) return provider;
        }
        return null;
    }

    private static String errorCode(int status) {
        return switch (status) {
            case 401, 403 -> "AUTH_REJECTED";
            case 429 -> "RATE_LIMITED";
            case 503 -> "PROVIDER_UNAVAILABLE";
            default -> "HTTP_" + status;
        };
    }

    private static long retryAfterMillis(HttpHeaders headers) {
        if (headers == null) return 0;
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return 0;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim()))).toMillis();
        } catch (NumberFormatException ignored) {
            try {
                return Math.max(0, Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis());
            } catch (Exception invalidDate) {
                return 0;
            }
        }
    }
}
