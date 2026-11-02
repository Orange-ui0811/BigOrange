package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrendResponseParser {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public Map<String, Object> parse(String content) {
        if (content == null || content.isBlank()) return Map.of("error", "AI返回为空");
        String json = content.trim();
        if (json.startsWith("```") && json.endsWith("```")) {
            int newline = json.indexOf('\n');
            if (newline >= 0) json = json.substring(newline + 1, json.length() - 3).trim();
        }
        try {
            Map<String, Object> result = mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            result.put("raw", json);
            return result;
        } catch (Exception error) { return Map.of("error", "AI返回的JSON格式无效", "raw", content); }
    }
    public boolean complete(Map<String, Object> result) {
        if (result == null || result.containsKey("error")) return false;
        if (!(result.get("analysis") instanceof Map<?, ?> analysis)
                || !(result.get("learning") instanceof Map<?, ?> learning)
                || !(result.get("recommendations") instanceof List<?> recommendations)) return false;
        if (!List.of("trend", "prospect", "demand", "summary").stream().allMatch(k -> text(analysis.get(k)))
                || !text(learning.get("timeline"))
                || !(learning.get("steps") instanceof List<?> steps) || steps.isEmpty()
                || !steps.stream().allMatch(this::text)
                || recommendations.size() > 3) return false;
        for (Object value : recommendations) {
            if (!(value instanceof Map<?, ?> item)
                    || !List.of("name", "description", "difficulty", "difficultyReason", "vibeCodingReason",
                    "recommendReason", "prerequisites").stream().allMatch(k -> text(item.get(k)))) return false;
            if (!(item.get("score") instanceof Number score) || !Double.isFinite(score.doubleValue())
                    || score.doubleValue() < 0 || score.doubleValue() > 100) return false;
            if (item.containsKey("references")) {
                if (!(item.get("references") instanceof List<?> references)) return false;
                for (Object reference : references) {
                    if (!(reference instanceof Map<?, ?> ref) || !text(ref.get("name")) || !text(ref.get("url"))) return false;
                }
            }
        }
        return true;
    }
    private boolean text(Object value) { return value instanceof String text && !text.isBlank(); }
}
