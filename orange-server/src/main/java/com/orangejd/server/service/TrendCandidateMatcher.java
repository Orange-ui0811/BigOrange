package com.orangejd.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic admission score for verified project catalog candidates. */
final class TrendCandidateMatcher {
    private TrendCandidateMatcher() {}

    static int score(List<String> topics, List<String> languages, String searchableText,
                     String primaryLanguage) {
        String text = safe(searchableText).toLowerCase(Locale.ROOT);
        List<List<String>> groups = topicGroups(topics);
        int topicHits = 0;
        for (List<String> group : groups) {
            if (group.stream().anyMatch(alias -> containsAlias(text, alias))) topicHits++;
        }
        if (!groups.isEmpty() && topicHits == 0) return -1;
        int language = 0;
        if (languages != null && !languages.isEmpty()) {
            String actual = safe(primaryLanguage).toLowerCase(Locale.ROOT);
            language = languages.stream().filter(value -> value != null)
                    .anyMatch(value -> actual.equals(value.strip().toLowerCase(Locale.ROOT))
                            || text.contains(value.strip().toLowerCase(Locale.ROOT))) ? 15 : 0;
        }
        return topicHits * 40 + language;
    }

    private static List<List<String>> topicGroups(List<String> topics) {
        List<List<String>> groups = new ArrayList<>();
        if (topics == null) return groups;
        for (String raw : topics) {
            String topic = safe(raw).toLowerCase(Locale.ROOT);
            if (topic.isBlank()) continue;
            if (topic.contains("rag") || topic.contains("知识库") || topic.contains("检索增强"))
                groups.add(List.of("rag", "知识库", "检索增强", "retrieval augmented"));
            else if (topic.contains("agent") || topic.contains("智能体"))
                groups.add(List.of("agent", "智能体", "tool calling", "function calling"));
            else if (topic.contains("spring"))
                groups.add(List.of("spring boot", "springboot", "spring"));
            else if (topic.contains("vue"))
                groups.add(List.of("vue", "vue.js", "vuejs"));
            else if (topic.contains("数据分析"))
                groups.add(List.of("数据分析", "pandas", "numpy", "jupyter", "数据可视化"));
            else if (topic.contains("机器学习") || topic.contains("模型训练"))
                groups.add(List.of("机器学习", "模型训练", "machine learning", "tensorflow", "pytorch", "scikit"));
            else groups.add(List.of(topic));
        }
        return groups;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static boolean containsAlias(String text, String alias) {
        boolean latin = alias.chars().allMatch(value -> value < 128);
        if (!latin) return text.contains(alias);
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])")
                .matcher(text).find();
    }
}
