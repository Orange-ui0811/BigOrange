package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RagBIndexRouter {
    private final RagBVectorRepository exact;
    private final RagBRedisStackRepository redisStack;

    public RagBIndexRouter(RagBVectorRepository exact, RagBRedisStackRepository redisStack) {
        this.exact = exact;
        this.redisStack = redisStack;
    }

    public Map<Long, String> index(RagBDocument document, RagBIndexGeneration generation, List<RagBChunk> chunks) {
        return backend(generation).index(document, generation, chunks);
    }

    public RagBVectorSearchResult searchWithTrace(RagBIndexGeneration generation, String query, int topK,
                                                   double threshold, RagBSearchConstraints constraints) {
        return backend(generation).searchWithTrace(generation, query, topK, threshold, constraints);
    }

    public String adapterType(RagBIndexGeneration generation) {
        return backend(generation).adapterType();
    }

    private RagBIndexBackend backend(RagBIndexGeneration generation) {
        String type = generation == null || generation.getAdapterType() == null
                ? "EXACT" : generation.getAdapterType().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HNSW_HYBRID" -> redisStack;
            case "EXACT" -> exact;
            default -> throw new IllegalStateException("不支持的 B 版索引适配器: " + type);
        };
    }
}
