package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TrendResponseParser {
    public Map<String, Object> parse(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isEmpty()) {
            result.put("error", "AI返回为空");
            return result;
        }
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        try {
            Map<String, Object> analysis = new LinkedHashMap<>();
            analysis.put("trend", extract(content, "\"trend\"\\s*:\\s*\"([^\"]+)\""));
            analysis.put("prospect", extract(content, "\"prospect\"\\s*:\\s*\"([^\"]+)\""));
            analysis.put("demand", extract(content, "\"demand\"\\s*:\\s*\"([^\"]+)\""));
            analysis.put("summary", extract(content, "\"summary\"\\s*:\\s*\"([^\"]+)\""));
            result.put("analysis", analysis);
            Map<String, Object> learning = new LinkedHashMap<>();
            learning.put("timeline", extract(content, "\"timeline\"\\s*:\\s*\"([^\"]+)\""));
            learning.put("tips", extract(content, "\"tips\"\\s*:\\s*\"([^\"]+)\""));
            String stepsValue = extract(content, "\"steps\"\\s*:\\s*\\[(.+?)\\]");
            if (stepsValue != null) {
                List<String> steps = new ArrayList<>();
                Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(stepsValue);
                while (matcher.find()) steps.add(matcher.group(1));
                learning.put("steps", steps);
            }
            result.put("learning", learning);
            result.put("raw", content);
            result.put("recommendations", recommendations(content));
            return result;
        } catch (Exception error) {
            result.put("analysis", content.length() > 300 ? content.substring(0, 300) : content);
            result.put("recommendations", Collections.emptyList());
            result.put("learning", new LinkedHashMap<>());
            result.put("raw", content);
            return result;
        }
    }

    public boolean complete(Map<String, Object> result) {
        if (result == null) return false;
        if (!(result.get("analysis") instanceof Map<?, ?> analysis)
                || !(result.get("learning") instanceof Map<?, ?> learning)
                || !(result.get("recommendations") instanceof List<?> recommendations)) return false;
        return text(analysis.get("trend")) && text(analysis.get("prospect"))
                && text(analysis.get("demand")) && text(analysis.get("summary"))
                && text(learning.get("timeline"))
                && learning.get("steps") instanceof List<?> steps && !steps.isEmpty()
                && !recommendations.isEmpty();
    }

    private List<Map<String, Object>> recommendations(String content) {
        List<Map<String, Object>> result = new ArrayList<>();
        String raw = extract(content, "\"recommendations\"\\s*:\\s*\\[(.+?)\\]\\s*,\\s*\"learning\"");
        if (raw == null) raw = extract(content, "\"recommendations\"\\s*:\\s*\\[(.+?)\\]");
        if (raw == null) return result;
        for (String source : raw.split("\\},\\s*\\{")) {
            String object = source.replaceAll("^\\s*\\{?", "{").replaceAll("\\}?\\s*$", "}");
            Map<String, Object> item = new LinkedHashMap<>();
            for (String field : List.of("name", "description", "difficulty", "difficultyReason",
                    "vibeCodingReason", "recommendReason", "prerequisites")) {
                item.put(field, extract(object, "\"" + field + "\"\\s*:\\s*\"([^\"]+)\""));
            }
            String score = extract(object, "\"score\"\\s*:\\s*(\\d+)");
            item.put("score", score == null ? 0 : Integer.parseInt(score));
            List<Object> references = references(object);
            if (!references.isEmpty()) item.put("references", references);
            if (item.get("name") != null) result.add(item);
        }
        return result;
    }

    private List<Object> references(String object) {
        String raw = extract(object, "\"references\"\\s*:\\s*\\[(.+?)\\]");
        List<Object> result = new ArrayList<>();
        if (raw == null) return result;
        Matcher objects = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*}").matcher(raw);
        while (objects.find()) result.add(Map.of("name", objects.group(1), "url", objects.group(2)));
        if (!result.isEmpty()) return result;
        Matcher strings = Pattern.compile("\"([^\"]+)\"").matcher(raw);
        while (strings.find()) if (!strings.group(1).startsWith("http")) result.add(strings.group(1));
        return result;
    }

    private String extract(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private boolean text(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
