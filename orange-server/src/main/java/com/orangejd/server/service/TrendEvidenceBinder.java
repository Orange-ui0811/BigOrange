package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Restricts real-project recommendations to the current verified candidate set. */
final class TrendEvidenceBinder {
    private static final Pattern REPOSITORY = Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> RESERVED_GITHUB_OWNERS = Set.of(
            "user-attachments", "features", "topics", "marketplace", "collections",
            "events", "sponsors", "settings", "login", "signup", "organizations",
            "orgs", "users", "apps", "about", "site");

    @SuppressWarnings("unchecked")
    static void bind(Map<String, Object> result, TrendContextBuilder.Retrieval retrieval,
                     List<BeginnerProjectView> beginner, List<CareerProjectView> career) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Map<String, Candidate> byUrl = new LinkedHashMap<>();
        Object evidence = retrieval.trace().get("evidence");
        if (evidence instanceof List<?> items) for (Object value : items) {
            if (!(value instanceof Map<?, ?> item)) continue;
            String evidenceId = String.valueOf(item.get("evidenceId"));
            String url = canonical(item.get("repositoryUrl"));
            if (url == null && item.get("text") instanceof String text) {
                var matcher = REPOSITORY.matcher(text);
                if (matcher.find()) url = canonical(matcher.group());
            }
            add(candidates, byUrl, evidenceId, url, Set.of(evidenceId));
        }
        for (var project : beginner) {
            String id = "B:" + project.id();
            add(candidates, byUrl, id, canonical(project.htmlUrl()), Set.of(id));
        }
        for (var project : career) {
            String id = "C:" + project.id();
            add(candidates, byUrl, id, canonical(project.htmlUrl()), Set.of(id));
        }
        if (!(result.get("recommendations") instanceof List<?> recommendations)) return;
        List<Object> grounded = new ArrayList<>();
        for (Object value : recommendations) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> item = (Map<String, Object>) raw;
            Candidate candidate = candidates.get(String.valueOf(item.getOrDefault("candidateId", "")));
            int rejected = 0;
            if (item.get("references") instanceof List<?> refs) {
                for (Object ref : refs) {
                    String url = ref instanceof Map<?, ?> map ? canonical(map.get("url")) : null;
                    Candidate matched = url == null ? null : byUrl.get(url);
                    if (matched == null) rejected++;
                    else if (candidate == null) candidate = matched;
                }
            }
            if (candidate == null) {
                item.put("references", List.of());
                item.put("evidenceIds", List.of());
                item.put("evidenceStatus", "UNSUPPORTED");
                item.put("unsupportedReferenceCount", rejected);
                if (candidates.isEmpty()) grounded.add(item);
                continue;
            }
            item.put("candidateId", candidate.id());
            item.put("references", List.of(Map.of(
                    "name", candidate.url().substring("https://github.com/".length()),
                    "url", candidate.url(), "evidenceIds", List.copyOf(candidate.evidenceIds()))));
            item.put("evidenceIds", List.copyOf(candidate.evidenceIds()));
            item.put("evidenceStatus", rejected > 0 ? "PARTIAL" : "VERIFIED_CANDIDATE");
            item.put("unsupportedReferenceCount", rejected);
            grounded.add(item);
        }
        result.put("recommendations", grounded);
    }

    private static void add(Map<String, Candidate> candidates, Map<String, Candidate> byUrl,
                            String id, String url, Set<String> evidenceIds) {
        if (id == null || id.isBlank() || url == null) return;
        Candidate value = new Candidate(id, url, evidenceIds);
        candidates.put(id, value);
        byUrl.putIfAbsent(url, value);
    }

    static String repositoryUrl(org.springframework.ai.document.Document document) {
        String direct = canonical(document.getMetadata().get("repositoryUrl"));
        if (direct != null) return direct;
        for (String field : List.of("docTitle", "sourceRef")) {
            String value = Objects.toString(document.getMetadata().get(field), "").split("\\|", 2)[0].strip();
            if (value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
                return canonical("https://github.com/" + value);
        }
        // Legacy chunks may not have repositoryUrl metadata. A link in their body is
        // accepted only when its repository slug matches the document title; otherwise
        // dependency/citation links can be mistaken for the source repository.
        String title = Objects.toString(document.getMetadata().get("docTitle"), "").split("\\|", 2)[0].strip();
        if (!title.matches("[A-Za-z0-9_.-]+")) return null;
        var matcher = REPOSITORY.matcher(Objects.toString(document.getText(), ""));
        while (matcher.find()) {
            String candidate = canonical(matcher.group());
            if (candidate != null && candidate.substring(candidate.lastIndexOf('/') + 1).equalsIgnoreCase(title))
                return candidate;
        }
        return null;
    }

    static String canonical(Object value) {
        if (!(value instanceof String url)) return null;
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null) return null;
            String path = uri.getPath().replaceAll("/+$", "");
            if (!path.matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return null;
            String owner = path.substring(1, path.indexOf('/', 1)).toLowerCase(Locale.ROOT);
            if (RESERVED_GITHUB_OWNERS.contains(owner)) return null;
            return "https://github.com" + path.replaceAll("\\.git$", "").toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException error) { return null; }
    }

    private record Candidate(String id, String url, Set<String> evidenceIds) {}
}
