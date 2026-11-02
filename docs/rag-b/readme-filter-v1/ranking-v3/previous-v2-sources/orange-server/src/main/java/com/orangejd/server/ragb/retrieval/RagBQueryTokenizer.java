package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.search.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RagBQueryTokenizer {
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "is", "are", "was", "were",
            "to", "of", "and", "or", "in", "for", "with", "on", "by", "how", "what", "which",
            "can", "does", "do", "it", "its", "that", "this", "there");

    public static List<String> tokenize(String input) {
        Set<String> terms = new LinkedHashSet<>();
        for (String part : input.replaceAll("[^\\p{L}\\p{N}_-]+", " ").trim().split("\\s+")) {
            if (!part.isBlank() && !STOP_WORDS.contains(part.toLowerCase(java.util.Locale.ROOT))) terms.add(part);
            if (terms.size() >= 12) break;
        }
        return List.copyOf(terms);
    }

    public static String escapeQueryTerm(String value) {
        return value.replace("\\", "\\\\").replace("-", "\\-");
    }
}
