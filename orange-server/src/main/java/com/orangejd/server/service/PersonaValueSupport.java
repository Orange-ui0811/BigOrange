package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

final class PersonaValueSupport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PersonaValueSupport() {
    }

    static Map<String, Double> config(Object raw, Map<String, Double> defaults) {
        Map<String, Double> result = new LinkedHashMap<>(defaults);
        if (raw == null) return result;
        try {
            Map<String, Double> saved = raw instanceof Map<?, ?> map
                    ? JSON.convertValue(map, new TypeReference<>() {})
                    : JSON.readValue(String.valueOf(raw), new TypeReference<>() {});
            saved.forEach((key, value) -> {
                if (defaults.containsKey(key) && value != null) result.put(key, value);
            });
        } catch (Exception ignored) {
            // Invalid legacy JSON falls back to safe defaults.
        }
        return result;
    }

    static Map<String, Double> validatedConfig(Map<String, Double> requested,
                                                Map<String, Double> current,
                                                Map<String, double[]> ranges,
                                                String message) {
        Map<String, Double> result = new LinkedHashMap<>(current);
        if (requested == null) return result;
        for (Map.Entry<String, Double> entry : requested.entrySet()) {
            double[] range = ranges.get(entry.getKey());
            Double value = entry.getValue();
            if (range == null || value == null || !Double.isFinite(value)
                    || value < range[0] || value > range[1]) throw new BaseException(message);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    static String json(Object value, String message) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception error) {
            throw new BaseException(message);
        }
    }

    static Map<String, Object> objectValue(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            if (raw instanceof Map<?, ?> map) return JSON.convertValue(map, new TypeReference<>() {});
            return JSON.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    static String requiredText(String value) {
        if (value == null || value.isBlank()) throw new BaseException("请输入画像内容");
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
}
