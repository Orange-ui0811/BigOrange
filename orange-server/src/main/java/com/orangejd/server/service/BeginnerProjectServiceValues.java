package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class BeginnerProjectServiceValues {
    public static int value(Integer value) {
        return value == null ? 0 : value;
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static List<String> cleanList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().limit(20).toList();
    }

    public static String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    public static String abbreviate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static String json(ObjectMapper objectMapper, Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("项目数据无法序列化", e); }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
