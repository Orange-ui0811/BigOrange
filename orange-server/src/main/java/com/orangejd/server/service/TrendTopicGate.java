package com.orangejd.server.service;

import org.springframework.ai.document.Document;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative lexical admission gate, not a semantic relevance classifier. */
final class TrendTopicGate {
    private static final Pattern LATIN = Pattern.compile("[a-z][a-z0-9+#.]*");
    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Set<String> GENERAL = Set.of("ai", "python", "java", "javascript", "typescript", "c", "c++", "go");
    static boolean accepts(Document doc, TrendAnalysisInput input) {
        String topic = ((input.topics() == null ? "" : String.join(" ", input.topics())) + " "
                + Objects.toString(input.customTopics(), "")).toLowerCase(Locale.ROOT);
        String text = (Objects.toString(doc.getMetadata().get("docTitle"), "") + " " + doc.getText()).toLowerCase(Locale.ROOT);
        List<String> recognizedAliases = aliases(topic);
        for (String alias : recognizedAliases) {
            boolean latinAlias = alias.chars().allMatch(value -> value < 128);
            if (latinAlias ? containsToken(text, alias) : text.contains(alias)) return true;
        }
        // A known domain must hit one of its specific aliases. Falling through to generic
        // fragments such as "开源项目" would admit unrelated repositories.
        if (!recognizedAliases.isEmpty()) return false;
        List<String> anchors = new ArrayList<>();
        var latin = LATIN.matcher(topic);
        while (latin.find()) if (latin.group().length() >= 2 && !GENERAL.contains(latin.group())) anchors.add(latin.group());
        if (!anchors.isEmpty()) return anchors.stream().allMatch(token -> containsToken(text, token));
        var chinese = CHINESE.matcher(topic);
        while (chinese.find()) {
            String phrase = chinese.group();
            int n = Math.min(4, phrase.length());
            for (int i = 0; i <= phrase.length() - n; i++) if (text.contains(phrase.substring(i, i+n))) return true;
        }
        // Without specific topic terms, language alone is not a reliable rejection signal.
        return !CHINESE.matcher(topic).find();
    }
    private static boolean containsToken(String text, String token) {
        if (token.equals("rag") && (text.contains("知识库") || text.contains("检索增强"))) return true;
        if (token.equals("agent") && text.contains("智能体")) return true;
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(token) + "(?![a-z0-9])").matcher(text).find();
    }
    static String lexicalQuery(TrendAnalysisInput input) {
        String topic = ((input.topics() == null ? "" : String.join(" ", input.topics())) + " "
                + Objects.toString(input.customTopics(), "")).toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>(aliases(topic));
        var matcher = LATIN.matcher(topic);
        while (matcher.find()) if (!GENERAL.contains(matcher.group())) {
            // Only alphanumeric terms are inserted into the Redis query; no user query operators.
            String term=matcher.group().replaceAll("[^a-z0-9]", "");
            if (!term.isBlank()) terms.add(term);
        }
        var chinese = CHINESE.matcher(topic);
        while (chinese.find()) {
            String phrase = chinese.group();
            if (phrase.length() <= 8) terms.add(phrase);
        }
        return String.join("|", terms.stream()
                .map(value -> value.replaceAll("[^a-z0-9\\p{IsHan}]", ""))
                .filter(value -> !value.isBlank()).distinct().limit(8).toList());
    }

    private static List<String> aliases(String topic) {
        List<String> values = new ArrayList<>();
        if (topic.contains("rag") || topic.contains("知识库") || topic.contains("检索增强"))
            values.addAll(List.of("rag", "知识库", "检索增强"));
        if (topic.contains("agent") || topic.contains("智能体"))
            values.addAll(List.of("agent", "智能体", "tool calling", "function calling"));
        if (topic.contains("spring"))
            values.addAll(List.of("spring boot", "springboot", "spring"));
        if (topic.contains("vue"))
            values.addAll(List.of("vue", "vue.js", "vuejs"));
        if (topic.contains("数据分析"))
            values.addAll(List.of("数据分析", "pandas", "numpy", "jupyter", "可视化"));
        if (topic.contains("机器学习") || topic.contains("模型训练"))
            values.addAll(List.of("机器学习", "模型训练", "machine learning", "tensorflow", "pytorch", "scikit"));
        if (topic.contains("3d打印") || topic.contains("3d printing") || topic.contains("增材制造"))
            values.addAll(List.of("3d打印", "3d printing", "3d printer", "增材制造", "additive manufacturing",
                    "g-code", "gcode"));
        return values;
    }
    static String projectKey(Document doc) {
        String title = Objects.toString(doc.getMetadata().get("docTitle"), "").split("\\|", 2)[0].strip();
        if (title.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return "repo:" + title.toLowerCase(Locale.ROOT);
        Object projectId = doc.getMetadata().get("projectId");
        if (projectId instanceof String value && !value.isBlank()) return "project:" + value;
        Object ref = doc.getMetadata().get("sourceRef");
        if (ref instanceof String value && !value.isBlank()) return value;
        Object id = doc.getMetadata().get("docId");
        return id == null ? doc.getId() : id.toString();
    }
}
