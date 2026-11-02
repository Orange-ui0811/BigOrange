package com.orangejd.server.ragb.retrieval;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Applies the same hard constraints for every B-index backend. */
@Component
public class RagBConstraintMatcher {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"), Map.entry("node", "javascript"), Map.entry("nodejs", "javascript"),
            Map.entry("node.js", "javascript"), Map.entry("ts", "typescript"),
            Map.entry("vuejs", "vue"), Map.entry("vue.js", "vue"),
            Map.entry("reactjs", "react"), Map.entry("react.js", "react"),
            Map.entry("golang", "go"), Map.entry("py", "python"));

    public String filterReason(Map<?, ?> raw, RagBSearchConstraints constraints) {
        return filterReason(key -> string(raw.get(key)), constraints);
    }

    public String filterReason(Function<String, String> value, RagBSearchConstraints constraints) {
        if (constraints == null) return null;
        if(!constraints.documentIds().isEmpty()) {
            try { if(!constraints.documentIds().contains(Long.parseLong(value.apply("documentId")))) return "DOCUMENT_SCOPE_MISMATCH"; }
            catch(RuntimeException invalid) { return "DOCUMENT_SCOPE_MISMATCH"; }
        }
        String sourceType = value.apply("sourceType");
        if (!constraints.sourceTypes().isEmpty() && !constraints.sourceTypes().contains(sourceType)) {
            return "SOURCE_TYPE_MISMATCH";
        }
        if (!constraints.requiredLanguages().isEmpty()) {
            Set<String> actual = languageTokens(value.apply("language") + " " + value.apply("techStack"));
            if (actual.isEmpty()) return "LANGUAGE_UNKNOWN";
            boolean matches = constraints.requiredLanguages().stream()
                    .map(this::canonical).allMatch(actual::contains);
            if (!matches) return "LANGUAGE_MISMATCH";
        }
        if (constraints.maxDurationDays() != null) {
            String days = value.apply("durationMaxDays");
            if (days == null || days.isBlank()) return "DURATION_UNKNOWN";
            try {
                if (Double.parseDouble(days) > constraints.maxDurationDays()) return "DURATION_EXCEEDED";
            } catch (NumberFormatException ignored) {
                return "DURATION_UNKNOWN";
            }
        }
        if (constraints.difficulty() != null && !constraints.difficulty().isBlank()) {
            String actual = value.apply("difficulty");
            if (actual == null || actual.isBlank()) return "DIFFICULTY_UNKNOWN";
            if (!canonical(actual).contains(canonical(constraints.difficulty()))) return "DIFFICULTY_MISMATCH";
        }
        String reason = requirement(value, "requiresDocker", constraints.allowDocker(), "DOCKER");
        if (reason != null) return reason;
        reason = requirement(value, "requiresDatabase", constraints.allowDatabase(), "DATABASE");
        return reason != null ? reason : requirement(value, "requiresSecrets", constraints.allowSecrets(), "SECRETS");
    }

    public Set<String> languageTokens(String input) {
        Set<String> result = new LinkedHashSet<>();
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT)
                .replace("c++", " cpp ").replace("c#", " csharp ");
        for (String token : normalized.split("[^\\p{L}\\p{N}.+#]+")) {
            String value = canonical(token);
            if (!value.isBlank()) result.add(value);
        }
        return Set.copyOf(result);
    }

    public String canonical(String input) {
        String value = input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
        return ALIASES.getOrDefault(value, value);
    }

    private String requirement(Function<String, String> value, String key, Boolean allowed, String label) {
        if (allowed == null || allowed) return null;
        if (!Boolean.parseBoolean(value.apply("requirementsKnown"))) return label + "_UNKNOWN";
        return Boolean.parseBoolean(value.apply(key)) ? label + "_NOT_ALLOWED" : null;
    }

    private String string(Object value) { return value == null ? "" : value.toString(); }
}
