package com.orangejd.server.ragb.retrieval;

import java.util.List;
import java.util.regex.Pattern;

/** Vector relevance leads; names receive a boost only when explicitly mentioned. */
public final class RagBFusionScorer {
    private static final int RRF_K = 60;
    public static final double LEXICAL_WEIGHT = 0.2;

    private RagBFusionScorer() {}

    public static double score(String query, String title, String sourceRef,
                               int vectorRank, int lexicalRank, int nameRank) {
        boolean named = exactNameMatch(query, title, sourceRef);
        return reciprocal(vectorRank) + LEXICAL_WEIGHT * reciprocal(lexicalRank)
                + (named ? 3.0 * reciprocal(nameRank) + 0.1 : 0);
    }

    private static double reciprocal(int rank) { return rank > 0 ? 1.0 / (RRF_K + rank) : 0; }

    public static boolean exactNameMatch(String query, String title, String sourceRef) {
        if (query == null) return false;
        for (String value : List.of(title == null ? "" : title, sourceRef == null ? "" : sourceRef)) {
            String candidate = value.split("\\|", 2)[0];
            int slash = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
            if (slash >= 0) candidate = candidate.substring(slash + 1);
            if (candidate.replaceAll("[^\\p{L}\\p{N}]", "").length() < 4) continue;
            // Latin boundaries prevent Java matching JavaScript, while allowing Chinese suffixes.
            if (Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(candidate) + "(?![a-z0-9])")
                    .matcher(query).find()) return true;
        }
        return false;
    }
}
