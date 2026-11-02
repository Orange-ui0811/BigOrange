package com.orangejd.server.ragb.ingest;

import com.orangejd.server.ragb.domain.RagBDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RagBMetadataExtractor {
    private static final Pattern SECTION = Pattern.compile("(?ms)^##\\s+([^\\r\\n]+)\\s*$\\R(.*?)(?=^##\\s|\\z)");
    private static final Pattern REPOSITORY = Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*(?:[～~\\-至到]\\s*(\\d+))?\\s*天");
    private static final Pattern WEEKS = Pattern.compile("(\\d+)\\s*(?:[～~\\-至到]\\s*(\\d+))?\\s*(?:周|星期)");
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*(?:[～~\\-至到]\\s*(\\d+))?\\s*(?:个)?月");

    public Map<String, Object> extract(RagBDocument document, String content) {
        return extract(document, content, Map.of());
    }

    public Map<String, Object> extract(RagBDocument document, String content,
                                       Map<String, Object> authoritativeMetadata) {
        Map<String, String> sections = sections(content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", document.getSourceType());
        result.put("sourceRef", document.getSourceRef());
        put(result, "language", sections.get("主要语言"));
        put(result, "techStack", sections.get("技术栈"));
        put(result, "difficulty", sections.get("难度"));
        String duration = sections.get("预计周期");
        put(result, "duration", duration);
        Integer maxDays = maximumDays(duration);
        if (maxDays != null) result.put("durationMaxDays", maxDays);
        String requirements = sections.get("环境要求");
        if (requirements != null) {
            result.put("requiresDocker", contains(requirements, "docker"));
            result.put("requiresDatabase", contains(requirements, "数据库", "mysql", "postgres", "redis"));
            result.put("requiresSecrets", contains(requirements, "密钥", "api key", "secret"));
            result.put("requirementsKnown", true);
        }
        Matcher repository = REPOSITORY.matcher(content == null ? "" : content);
        if (repository.find()) result.put("repositoryUrl", repository.group().replaceAll("[/.]+$", ""));
        String ref = document.getSourceRef();
        int separator = ref == null ? -1 : Math.max(ref.indexOf(':'), ref.indexOf('_'));
        if (separator >= 0 && separator + 1 < ref.length()) result.put("businessId", ref.substring(separator + 1));
        if (authoritativeMetadata != null) authoritativeMetadata.forEach((key, value) -> {
            if (value != null) result.put(key, value);
        });
        Integer authoritativeDays = maximumDays(String.valueOf(result.getOrDefault("duration", "")));
        if (authoritativeDays != null) result.put("durationMaxDays", authoritativeDays);
        return result;
    }

    private Map<String, String> sections(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = SECTION.matcher(content == null ? "" : content);
        while (matcher.find()) result.putIfAbsent(matcher.group(1).trim(), matcher.group(2).trim());
        return result;
    }

    private Integer maximumDays(String value) {
        if (value == null || value.isBlank()) return null;
        List<Integer> values = new ArrayList<>();
        collect(values, DAYS.matcher(value), 1);
        collect(values, WEEKS.matcher(value), 7);
        collect(values, MONTHS.matcher(value), 30);
        return values.stream().max(Integer::compareTo).orElse(null);
    }

    private void collect(List<Integer> values, Matcher matcher, int multiplier) {
        while (matcher.find()) values.add(Integer.parseInt(matcher.group(2) == null ? matcher.group(1) : matcher.group(2)) * multiplier);
    }

    private boolean contains(String value, String... needles) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) if (normalized.contains(needle)) return true;
        return false;
    }

    private void put(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }
}
