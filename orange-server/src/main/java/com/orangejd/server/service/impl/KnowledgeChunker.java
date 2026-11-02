package com.orangejd.server.service.impl;

import java.util.ArrayList;
import java.util.List;

/** Shared lossless character chunker. Boundaries prefer Markdown/newline breaks. */
final class KnowledgeChunker {
    static final int CHUNK_SIZE = 500;
    static final int OVERLAP = 50;
    static final int MAX_CHUNKS = 100;

    private KnowledgeChunker() {}

    static List<String> split(String content) {
        if (content == null || content.isBlank()) return List.of();
        String text = content.replace("\r\n", "\n").replace('\r', '\n');
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length() && result.size() < MAX_CHUNKS) {
            int hardEnd = Math.min(start + CHUNK_SIZE, text.length());
            int end = preferredBoundary(text, start, hardEnd);
            String chunk = text.substring(start, end).trim();
            String plain = chunk.replaceAll("\\s+", "").replaceAll("[#*\\-`>|]+", "");
            if (plain.length() >= 10) result.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
        return List.copyOf(result);
    }

    private static int preferredBoundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) return text.length();
        int minimum = start + CHUNK_SIZE / 2;
        int heading = text.lastIndexOf("\n#", hardEnd);
        if (heading >= minimum) return heading + 1;
        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= minimum) return paragraph + 2;
        int newline = text.lastIndexOf('\n', hardEnd);
        return newline >= minimum ? newline + 1 : hardEnd;
    }
}
