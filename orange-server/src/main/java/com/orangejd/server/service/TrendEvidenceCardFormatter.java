package com.orangejd.server.service;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Compact deterministic evidence for prompts; full text stays in the audit trace. */
final class TrendEvidenceCardFormatter {
    static final int MAX_CARD_CHARS = 220;
    static final int MAX_FALLBACK_CARD_CHARS = 360;
    static final int MAX_CONTEXT_CHARS = 1100;
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^]]*)]\\([^)]*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]*\\)");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern MARKDOWN = Pattern.compile("[`*_#>|]+|(?:^|\\s)-\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> FACT_TERMS = List.of(
            "用于", "支持", "提供", "实现", "转换", "生成", "工具", "平台", "框架", "组件", "项目",
            "python", "java", "typescript", "javascript", "vue", "vite", "react", "canvas", "webgl",
            "glsl", "rag", "知识库", "agent", "智能体", "mcp", "docker", "jupyter", "g-code", "gcode");
    private static final List<String> NOISE_TERMS = List.of(
            "badge", "赞助", "许可证", "license", "立即获取", "构建服务器", "行为准则", "社区论坛");
    private static final List<String> PURPOSE_TERMS = List.of(
            "用于", "支持", "提供", "实现", "转换", "生成", "工具", "平台", "框架", "组件", "项目",
            "系统", "服务", "问答", "检索", "分析", "可视化", "打印", "采集");

    private TrendEvidenceCardFormatter() {}

    record Rendered(String text, List<String> cards, List<String> modes, int originalChars) {
        int renderedChars() { return text.length(); }
        long fallbackCount() { return modes.stream().filter("RAW_FALLBACK"::equals).count(); }
    }

    static Rendered render(List<Document> documents) {
        return render(documents, true);
    }

    static Rendered renderCompactOnly(List<Document> documents) {
        return render(documents, false);
    }

    private static Rendered render(List<Document> documents, boolean adaptiveFallback) {
        if (documents == null || documents.isEmpty()) return new Rendered("", List.of(), List.of(), 0);
        List<String> cards = new ArrayList<>();
        List<String> modes = new ArrayList<>();
        int originalChars = 0;
        int contextChars = 0;
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            originalChars += Objects.toString(document.getText(), "").length();
            CardData rendered = cardData(document, index + 1, adaptiveFallback);
            String card = rendered.text();
            int separatorChars = cards.isEmpty() ? 0 : 2;
            if (contextChars + separatorChars + card.length() > MAX_CONTEXT_CHARS) break;
            cards.add(card);
            modes.add(rendered.mode());
            contextChars += separatorChars + card.length();
        }
        return new Rendered(String.join("\n\n", cards), List.copyOf(cards), List.copyOf(modes), originalChars);
    }

    static String card(Document document, int number) {
        return cardData(document, number, true).text();
    }

    private static CardData cardData(Document document, int number, boolean adaptiveFallback) {
        String evidenceId = "E" + number;
        String repository = repositorySlug(TrendEvidenceBinder.repositoryUrl(document));
        FactData factData = facts(document.getText());
        boolean fallback = adaptiveFallback && !factData.sufficient();
        String mode = fallback ? "RAW_FALLBACK" : "CARD";
        String prefix = "[candidateId=" + evidenceId + ";evidenceId=" + evidenceId
                + ";repo=" + clipped(repository, 58) + (fallback ? ";mode=RAW_FALLBACK" : "") + "] facts=";
        int limit = fallback ? MAX_FALLBACK_CARD_CHARS : MAX_CARD_CHARS;
        if (prefix.length() >= limit) return new CardData(clipped(prefix, limit), mode);
        String content = fallback ? clean(document.getText()) : factData.text();
        return new CardData(prefix + clipped(content, limit - prefix.length()), mode);
    }

    private static FactData facts(String text) {
        String summary = firstSection(text, "项目摘要", "中文简介");
        String description = firstSection(text, "项目是什么", "README 摘要");
        String stack = firstSection(text, "技术栈", "主要语言");
        String capabilities = firstSection(text, "可证明能力", "核心功能", "功能");
        StringBuilder facts = new StringBuilder();
        appendFact(facts, "摘要", summary);
        appendFact(facts, "定位", description);
        appendFact(facts, "能力", capabilities);
        appendFact(facts, "技术", stack);
        String excerpt = informativeExcerpt(text);
        if (facts.length() < 150 && !excerpt.isBlank()) appendFact(facts, "证据", excerpt);
        String result = facts.isEmpty() ? excerpt : facts.toString();
        boolean structuredPurpose = !summary.isBlank() || !description.isBlank() || !capabilities.isBlank();
        return new FactData(result, structuredPurpose || informativeEnough(excerpt));
    }

    private static boolean informativeEnough(String excerpt) {
        if (excerpt == null || excerpt.length() < 24) return false;
        String lower = excerpt.toLowerCase(Locale.ROOT);
        int factTerms = 0;
        for (String term : FACT_TERMS) if (lower.contains(term)) factTerms++;
        boolean hasPurpose = PURPOSE_TERMS.stream().anyMatch(lower::contains);
        return factTerms >= 2 && hasPurpose;
    }

    private static String firstSection(String text, String... headings) {
        for (String heading : headings) {
            String value = section(text, heading);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String section(String text, String heading) {
        if (text == null || text.isBlank()) return "";
        Pattern pattern = Pattern.compile("(?ms)^\\s*#{1,6}\\s*" + Pattern.quote(heading)
                + "\\s*$\\s*(.*?)(?=^\\s*#{1,6}\\s|\\z)");
        var matcher = pattern.matcher(text.replace("\r\n", "\n").replace('\r', '\n'));
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private static void appendFact(StringBuilder target, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (!target.isEmpty()) target.append(';');
        target.append(label).append(':').append(value);
    }

    private static String informativeExcerpt(String text) {
        if (text == null || text.isBlank()) return "";
        String prepared = markdownText(text.replace("\r\n", "\n").replace('\r', '\n'));
        String[] fragments = prepared.split("(?<=[。！？.!?])|\\n+");
        List<Clause> clauses = new ArrayList<>();
        for (int index = 0; index < fragments.length; index++) {
            String value = clean(fragments[index]);
            if (value.length() < 6) continue;
            clauses.add(new Clause(index, value, informationScore(value)));
        }
        if (clauses.isEmpty()) return clean(prepared);
        List<Clause> informative = clauses.stream().filter(clause -> clause.score() > 0).toList();
        if (informative.isEmpty()) informative = clauses;
        return informative.stream()
                .sorted(Comparator.comparingInt(Clause::score).reversed().thenComparingInt(Clause::index))
                .limit(3)
                .sorted(Comparator.comparingInt(Clause::index))
                .map(Clause::text).reduce((left, right) -> left + "; " + right).orElse("");
    }

    private static int informationScore(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : FACT_TERMS) if (lower.contains(term)) score += 2;
        for (String term : NOISE_TERMS) if (lower.contains(term)) score -= 3;
        if (value.length() > 180) score--;
        return score;
    }

    private static String repositorySlug(String url) {
        if (url == null || url.isBlank()) return "unknown";
        String prefix = "https://github.com/";
        return url.toLowerCase(Locale.ROOT).startsWith(prefix) ? url.substring(prefix.length()) : url;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "";
        String withoutUrls = URL.matcher(markdownText(value)).replaceAll(" ");
        String withoutMarkdown = MARKDOWN.matcher(withoutUrls).replaceAll(" ");
        return WHITESPACE.matcher(withoutMarkdown).replaceAll(" ").strip();
    }

    private static String markdownText(String value) {
        String images = MARKDOWN_IMAGE.matcher(value).replaceAll(" $1 ");
        String links = MARKDOWN_LINK.matcher(images).replaceAll(" $1 ");
        return HTML.matcher(links).replaceAll(" ");
    }

    private static String clipped(String value, int limit) {
        if (limit <= 0 || value == null || value.isEmpty()) return "";
        if (value.length() <= limit) return value;
        if (limit == 1) return "…";
        return value.substring(0, limit - 1).stripTrailing() + "…";
    }

    private record Clause(int index, String text, int score) {}
    private record FactData(String text, boolean sufficient) {}
    private record CardData(String text, String mode) {}
}
