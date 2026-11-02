package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CareerDiscoveryCandidateSelector {
    static final List<String> CATEGORY_ORDER = List.of(
            "AGENT_NATIVE_APP",
            "KNOWLEDGE_TO_SKILL",
            "AI_DESIGN_ASSISTANCE",
            "AGENT_APPLICATION",
            "KNOWLEDGE_TOOL",
            "AI_DESIGN_TOOL",
            "ENGINEERING_QUALITY",
            "FULLSTACK_PRODUCT");

    public List<String> select(List<Candidate> values, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 120));
        Map<String, Candidate> unique = new LinkedHashMap<>();
        for (Candidate value : values) {
            if (value == null || value.fullName() == null || value.fullName().isBlank()) continue;
            unique.merge(value.fullName(), value,
                    (left, right) -> left.score() >= right.score() ? left : right);
        }

        Comparator<Candidate> order = Comparator.comparingInt(Candidate::score).reversed()
                .thenComparing(Candidate::fullName);
        Map<String, List<Candidate>> byCategory = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            byCategory.put(category, unique.values().stream()
                    .filter(value -> category.equals(value.category()))
                    .sorted(order)
                    .toList());
        }

        Set<String> selected = new LinkedHashSet<>();
        int round = 0;
        boolean added;
        do {
            added = false;
            for (String category : CATEGORY_ORDER) {
                List<Candidate> candidates = byCategory.get(category);
                if (round < candidates.size() && selected.add(candidates.get(round).fullName())) {
                    added = true;
                    if (selected.size() == limit) return List.copyOf(selected);
                }
            }
            round++;
        } while (added);

        List<Candidate> remaining = new ArrayList<>(unique.values());
        remaining.sort(order);
        for (Candidate candidate : remaining) {
            selected.add(candidate.fullName());
            if (selected.size() == limit) break;
        }
        return List.copyOf(selected);
    }

    public record Candidate(String fullName, String category, int score) {}
}
