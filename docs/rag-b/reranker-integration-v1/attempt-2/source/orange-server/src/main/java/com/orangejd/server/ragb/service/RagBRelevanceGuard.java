package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RagBRelevanceGuard {
    private static final List<String> PRICE_QUESTION = List.of("价格", "费用", "收费", "多少钱", "price", "pricing", "cost");
    private static final List<String> PRICE_EVIDENCE = List.of("价格", "费用", "收费", "免费", "付费", "人民币", "美元",
            "元/月", "元/年", "$", "¥", "price", "pricing", "cost", "free", "paid");
    private static final List<String> LICENSE_CONTEXT = List.of("商业", "授权", "许可证", "许可", "commercial",
            "license", "licensing");

    public Decision assess(String query, List<RagBSearchHit> hits) {
        if (hits == null || hits.isEmpty()) return new Decision(false, "NO_MATCH");
        String question = normalize(query);
        if (containsAny(question, PRICE_QUESTION) && containsAny(question, LICENSE_CONTEXT)
                && hits.stream().map(hit -> normalize(hit.title() + "\n" + hit.content()))
                .noneMatch(value -> containsAny(value, PRICE_EVIDENCE) && containsAny(value, LICENSE_CONTEXT))) {
            return new Decision(false, "PRICING_EVIDENCE_MISSING");
        }
        return new Decision(true, "RELEVANT");
    }

    private boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    public record Decision(boolean relevant, String reason) {}
}
