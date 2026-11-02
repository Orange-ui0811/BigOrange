package com.orangejd.server.ragb.ingest;

import java.util.*;
import java.util.regex.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;

/** Parses contiguous source blocks without making knowledge-admission decisions. */
final class ReadmeMarkdownParser {
    record Line(int start, int end, int number, String value) {}
    record Draft(int from, int to, String kind, boolean broken) {}
    static List<Line> lines(String text) {
        List<Line> result = new ArrayList<>();
        for (int at = 0, n = 1; at < text.length(); n++) {
            int end = text.indexOf('\n', at); end = end < 0 ? text.length() : end + 1;
            result.add(new Line(at, end, n, text.substring(at, end).replaceFirst("[\\r\\n]+$", ""))); at = end;
        }
        return result;
    }
    static List<Draft> parse(List<Line> lines) {
        List<Draft> out = new ArrayList<>();
        int n = lines.size(), from = 0, limit = n;
        // Repair only full-document wrappers with multiple real Markdown headings, never ordinary HTML examples.
        if (n >= 3 && has("^ {0,3}(`{3,}|~{3,})(?:html|markdown|md)\\s*$", lines.getFirst().value())
                && lines.subList(1, n - 1).stream().filter(l -> HEADING.matcher(l.value()).matches()).count() >= 2
                && lines.stream().noneMatch(l -> has("<(?:!doctype|html|head|body)\\b", l.value()))) {
            Matcher f = FENCE.matcher(lines.getFirst().value()); f.matches();
            if (closing(lines.getLast().value(), f.group(1))) { out.add(new Draft(0, 1, "WRAPPER", false)); from = 1; limit = n - 1; }
        }
        for (int i = from; i < limit;) {
            String value = lines.get(i).value(); int end = i + 1;
            if (value.isBlank()) {
                while (end < limit && lines.get(end).value().isBlank()) end++;
                out.add(new Draft(i, end, "SPACE", false)); i = end; continue;
            }
            Matcher f = FENCE.matcher(value);
            if (f.matches()) {
                while (end < limit && !closing(lines.get(end).value(), f.group(1))) end++;
                boolean broken = end == limit;
                if (!broken) end++;
                String body = joined(lines, i, end);
                broken |= has("(?m)^\\s*import\\s+['\"][^'\"\\r\\n]+$", body);
                out.add(new Draft(i, end, "CODE", broken)); i = end; continue;
            }
            if (HEADING.matcher(value).matches() || end < limit && has("^ {0,3}(?:={2,}|-{2,})\\s*$", lines.get(end).value()) && !has("^\\s*[-*+]\\s", value)) {
                if (!HEADING.matcher(value).matches()) end++;
                out.add(new Draft(i, end, "HEADING", false)); i = end; continue;
            }
            if (has("^\\s*<table\\b", value)) {
                boolean closed = has("</table\\s*>", value);
                while (end < limit && !closed) { closed = has("</table\\s*>", lines.get(end).value()); end++; }
                out.add(new Draft(i, end, "HTML_TABLE", !closed)); i = end; continue;
            }
            if (value.stripLeading().startsWith("|")) {
                while (end < limit && lines.get(end).value().stripLeading().startsWith("|")) end++;
                int columns = columns(value);
                boolean header = i + 1 < end && has("^\\s*\\|?[ :|-]+\\|\\s*$", lines.get(i + 1).value());
                int goodEnd = i + 2;
                if (header) while (goodEnd < end && columns(lines.get(goodEnd).value()) == columns) goodEnd++;
                if (header) out.add(new Draft(i, goodEnd, "TABLE", false));
                if (!header || goodEnd < end) out.add(new Draft(header ? goodEnd : i, end, "TABLE", true));
                i = end; continue;
            }
            if (standaloneLink(value)) {
                boolean broken = false;
                if (has("^\\s*<a\\s", value) && !has("</a\\s*>", value)) {
                    while (end < limit && !has("</a\\s*>", lines.get(end - 1).value())) end++;
                    broken = !has("</a\\s*>", lines.get(end - 1).value());
                }
                out.add(new Draft(i, end, "LINK", broken)); i = end; continue;
            }
            if (layoutTag(value)) { out.add(new Draft(i, end, "LAYOUT", false)); i = end; continue; }
            boolean indent = value.startsWith("    ") || value.startsWith("\t");
            while (end < limit && !lines.get(end).value().isBlank() && !special(lines, end, limit)) end++;
            out.add(new Draft(i, end, indent ? "INDENTED" : "PROSE", false)); i = end;
        }
        if (limit < n) out.add(new Draft(limit, n, "WRAPPER", false));
        List<Draft> grouped = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Draft d = out.get(i); int next = i + 1;
            if (next < out.size() && out.get(next).kind().equals("SPACE")) next++;
            String label = joined(lines, d.from(), d.to()).strip();
            if (d.kind().equals("PROSE") && label.length() < 120 && has("[:：](?:\\*\\*)?$", label)
                    && next < out.size() && out.get(next).kind().equals("LINK")
                    && has("^\\s*!\\[", joined(lines, out.get(next).from(), out.get(next).to()))) {
                grouped.add(new Draft(d.from(), out.get(next).to(), "FIGURE", false)); i = next;
            } else grouped.add(d);
        }
        return grouped;
    }
    static boolean special(List<Line> lines, int i, int limit) {
        String v = lines.get(i).value();
        return FENCE.matcher(v).matches() || HEADING.matcher(v).matches() || v.stripLeading().startsWith("|")
                || has("^\\s*<table\\b", v) || standaloneLink(v) || layoutTag(v)
                || i + 1 < limit && has("^ {0,3}(?:={2,}|-{2,})\\s*$", lines.get(i + 1).value());
    }
    static boolean standaloneLink(String value) {
        return has("^\\s*(?:\\[?!\\[|<a\\s)", value)
                || has("^\\s*(?:[-*+]\\s+)?[\\p{So}\\s\\uFE0F]*\\[[^\\n]+]\\([^\\n]+\\)\\s*(?:&#160;)?$", value);
    }
    static boolean layoutTag(String value) { return has("^\\s*</?(?:div|p|br|hr|span)(?:\\s[^>]*)?>\\s*$", value); }
    static boolean closing(String value, String opening) {
        return value.matches("^ {0,3}" + Pattern.quote(opening.substring(0, 1)) + "{" + opening.length() + ",}\\s*$");
    }
    static String joined(List<Line> lines, int from, int to) {
        StringBuilder b = new StringBuilder(); for (int i = from; i < to; i++) b.append(lines.get(i).value()).append('\n'); return b.toString();
    }
    static int columns(String line) {
        int pipes = 0; boolean escaped = false, code = false;
        for (char c : line.toCharArray()) { if (escaped) { escaped = false; continue; }
            if (c == '\\') escaped = true; else if (c == '`') code = !code; else if (c == '|' && !code) pipes++; }
        return pipes;
    }
}
