package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RagBCitationValidator {
    private static final Pattern CITATION = Pattern.compile("\\[C(\\d+)]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z][A-Za-z0-9_.+#/-]{1,}");

    public Validation validate(String answer, List<RagBSearchHit> evidence) {
        Map<Integer, RagBSearchHit> byNumber = new HashMap<>();
        for (int index = 0; index < evidence.size(); index++) byNumber.put(index + 1, evidence.get(index));
        List<SentenceCheck> checks = new ArrayList<>();
        for (String raw : split(answer)) {
            String sentence = raw.trim();
            if (sentence.isBlank() || ignorable(sentence)) continue;
            Matcher matcher = CITATION.matcher(sentence);
            List<Integer> citations = new ArrayList<>();
            while (matcher.find()) citations.add(Integer.parseInt(matcher.group(1)));
            if (citations.isEmpty()) {
                checks.add(new SentenceCheck(sentence, List.of(), false, "MISSING_CITATION", 0));
                continue;
            }
            List<RagBSearchHit> cited = citations.stream().distinct().map(byNumber::get).filter(java.util.Objects::nonNull).toList();
            if (cited.size() != citations.stream().distinct().count()) {
                checks.add(new SentenceCheck(sentence, List.copyOf(citations), false, "UNKNOWN_CITATION", 0));
                continue;
            }
            double support = support(sentence.replaceAll("\\[C\\d+]", ""), cited);
            checks.add(new SentenceCheck(sentence, List.copyOf(citations), support >= 0.12,
                    support >= 0.12 ? "SUPPORTED" : "LOW_TEXTUAL_SUPPORT", support));
        }
        long supported = checks.stream().filter(SentenceCheck::supported).count();
        boolean valid = !checks.isEmpty() && supported == checks.size();
        return new Validation(valid, checks.size(), (int) supported, List.copyOf(checks));
    }

    private List<String> split(String answer) {
        if (answer == null || answer.isBlank()) return List.of();
        String normalized = answer.replace("\r", "")
                .replaceAll("([。！？!?；;])((?:\\[C\\d+])+)", "$2$1");
        return List.of(normalized.split("(?<=[。！？!?；;])|\\n+"));
    }

    private boolean ignorable(String sentence) {
        String plain = sentence.replaceAll("[#>*`_\\-\\s]", "");
        return plain.length() <= 6 || sentence.matches("^#{1,6}\\s+.*") || sentence.matches("^\\|[-:| ]+\\|$");
    }

    private double support(String sentence, List<RagBSearchHit> cited) {
        Set<String> claims = features(sentence);
        if (claims.isEmpty()) return 1;
        Set<String> sources = new HashSet<>();
        cited.forEach(hit -> sources.addAll(features(hit.content())));
        long matched = claims.stream().filter(sources::contains).count();
        return (double) matched / claims.size();
    }

    private Set<String> features(String input) {
        Set<String> result = new HashSet<>();
        String value = input == null ? "" : input.toLowerCase(Locale.ROOT);
        Matcher latin = LATIN.matcher(value);
        while (latin.find()) result.add(latin.group());
        String chinese = value.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i + 1 < chinese.length(); i++) result.add(chinese.substring(i, i + 2));
        return result;
    }

    public record SentenceCheck(String sentence, List<Integer> citations, boolean supported,
                                String reason, double textualSupport) {}

    public record Validation(boolean valid, int checkedSentences, int supportedSentences,
                             List<SentenceCheck> sentences) {}
}
