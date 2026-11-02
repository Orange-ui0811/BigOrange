package com.orangejd.server.ragb.ingest;

import com.orangejd.server.ragb.config.RagBProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagBChunker {
    private final RagBProperties properties;

    public RagBChunker(RagBProperties properties) {
        this.properties = properties;
    }

    public List<ChunkDraft> split(String input) {
        return split(input, properties.getChunkPolicy());
    }

    public List<ChunkDraft> split(String input, String policy) {
        if (input == null || input.isBlank()) return List.of();
        String text = input.replace("\r\n", "\n").replace('\r', '\n');
        if (RagBTokenChunker.POLICY.equals(policy)) return new RagBTokenChunker().split(text, properties.getMaxChunks());
        if (!"CHAR_500_V1".equals(policy)) throw new IllegalArgumentException("不支持的分块策略: " + policy);
        List<Heading> headings = headings(text);
        List<ChunkDraft> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            if (chunks.size() >= properties.getMaxChunks()) {
                throw new IllegalArgumentException("文档分块数超过上限，未创建不完整索引");
            }
            int hardEnd = Math.min(start + properties.getChunkSize(), text.length());
            int end = boundary(text, start, hardEnd);
            if (end <= start) end = hardEnd;
            chunks.add(new ChunkDraft(chunks.size(), sectionAt(headings, start), start, end,
                    text.substring(start, end)));
            if (end == text.length()) break;
            start = Math.max(start + 1, end - properties.getChunkOverlap());
        }
        if (chunks.isEmpty() || chunks.get(chunks.size() - 1).endOffset() != text.length()) {
            throw new IllegalStateException("文档未被完整分块");
        }
        return List.copyOf(chunks);
    }

    private int boundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) return text.length();
        int minimum = start + Math.max(1, properties.getChunkSize() / 2);
        int heading = text.lastIndexOf("\n#", hardEnd);
        if (heading >= minimum) return heading + 1;
        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= minimum) return paragraph + 2;
        int newline = text.lastIndexOf('\n', hardEnd);
        return newline >= minimum ? newline + 1 : hardEnd;
    }

    private List<Heading> headings(String text) {
        List<Heading> result = new ArrayList<>();
        String[] path = new String[6];
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            int level = 0;
            while (level < line.length() && level < 6 && line.charAt(level) == '#') level++;
            if (level > 0 && level < line.length() && Character.isWhitespace(line.charAt(level))) {
                path[level - 1] = line.substring(level).trim();
                for (int i = level; i < path.length; i++) path[i] = null;
                result.add(new Heading(offset, String.join(" > ", present(path))));
            }
            offset += line.length() + 1;
        }
        return result;
    }

    private List<String> present(String[] values) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(value);
        return result;
    }

    private String sectionAt(List<Heading> headings, int offset) {
        String section = null;
        for (Heading heading : headings) {
            if (heading.offset() > offset) break;
            section = heading.path();
        }
        return section;
    }

    private record Heading(int offset, String path) {}

    public record ChunkDraft(int index, String sectionPath, int startOffset, int endOffset, String content) {}
}
