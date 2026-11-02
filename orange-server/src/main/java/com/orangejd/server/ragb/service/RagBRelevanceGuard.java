package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class RagBRelevanceGuard {
    private static final List<String> PRICE_QUESTION = List.of("价格", "费用", "收费", "多少钱", "price", "pricing", "cost");
    private static final List<String> LICENSE_CONTEXT = List.of("商业", "授权", "许可证", "许可", "commercial",
            "license", "licensing");
    private static final String LICENSE="(?:licen[cs](?:e|ing)|许可|授权)";
    private static final String PRICE="(?:price|pricing|cost|fees?|价格|费用|收费)";
    private static final String AMOUNT="(?:[$¥￥€£]\\s*\\d|\\d[\\d,.]*\\s*(?:元|美元|欧元|usd|eur|cny|rmb))";
    private static final Pattern LICENSE_PRICE=Pattern.compile(LICENSE+".{0,100}"+PRICE+".{0,60}"+AMOUNT+"|"+PRICE+".{0,60}"+LICENSE+".{0,60}"+AMOUNT,Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern FREE_LICENSE=Pattern.compile("(?:commercial|enterprise|business|商业|企业|商用).{0,20}"+LICENSE+".{0,30}(?:is\\s+free|免费|free\\s+of\\s+charge)|(?:commercial use|商业使用|商用)\\s*(?:is|为|可以)?\\s*(?:free|免费)",Pattern.CASE_INSENSITIVE);

    public Decision assess(String query, List<RagBSearchHit> hits) {
        if (hits == null || hits.isEmpty()) return new Decision(false, "NO_MATCH");
        String question = normalize(query);
        if (containsAny(question, PRICE_QUESTION) && containsAny(question, LICENSE_CONTEXT)
                && hits.stream().noneMatch(this::licensePricingEvidence)) {
            return new Decision(false, "PRICING_EVIDENCE_MISSING");
        }
        return new Decision(true, "RELEVANT");
    }

    private boolean licensePricingEvidence(RagBSearchHit hit){
        // Link labels and destinations do not supply unread prices. Generic 'free features' is not a licence price.
        String value=normalize(String.valueOf(hit.sectionPath())+"\n"+hit.content())
                .replaceAll("!?\\[[^]\\r\\n]*]\\([^\\r\\n)]*\\)"," ").replaceAll("<[^>]*>"," ");
        return LICENSE_PRICE.matcher(value).find()||FREE_LICENSE.matcher(value).find();
    }

    private boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    public record Decision(boolean relevant, String reason) {}
}
