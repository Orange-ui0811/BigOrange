package com.orangejd.server.ragb.ingest;

import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RagBBusinessMetadataProvider {
    private static final List<String> JOB_TECHNOLOGIES = List.of(
            "JavaScript", "TypeScript", "Python", "Kotlin", "Golang", "Java", "Vue", "React",
            "Spring Boot", "Spring", "Redis", "MySQL", "PostgreSQL", "Docker", "Kubernetes");

    private final RagBMapper mapper;
    private final RagBConstraintMatcher matcher;

    public RagBBusinessMetadataProvider(RagBMapper mapper, RagBConstraintMatcher matcher) {
        this.mapper = mapper;
        this.matcher = matcher;
    }

    public Map<String, Object> metadata(RagBDocument document) {
        Long id = businessId(document.getSourceRef());
        if (id == null) return Map.of();
        Map<String, Object> result = switch (document.getSourceType()) {
            case "BEGINNER_PROJECT" -> copy(mapper.selectBeginnerBusinessMetadata(id));
            case "CAREER_PROJECT" -> copy(mapper.selectCareerBusinessMetadata(id));
            case "JOB_POSTING" -> job(mapper.selectJobBusinessText(id), id);
            default -> Map.of();
        };
        return result == null ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Object> job(String text, Long id) {
        if (text == null || text.isBlank()) return Map.of();
        Set<String> tokens = matcher.languageTokens(text);
        List<String> technologies = JOB_TECHNOLOGIES.stream()
                .filter(value -> tokens.contains(matcher.canonical(value)) || phrase(text, value)).distinct().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        if (!technologies.isEmpty()) {
            result.put("language", String.join(",", technologies));
            result.put("techStack", String.join(",", technologies));
        }
        result.put("businessId", id.toString());
        return result;
    }

    private boolean phrase(String text, String value) {
        return text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (value != null) result.put(key, value); });
        result.put("requirementsKnown", true);
        return result;
    }

    private Long businessId(String sourceRef) {
        if (sourceRef == null) return null;
        int split = Math.max(sourceRef.lastIndexOf(':'), sourceRef.lastIndexOf('_'));
        if (split < 0 || split + 1 >= sourceRef.length()) return null;
        try { return Long.parseLong(sourceRef.substring(split + 1)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
