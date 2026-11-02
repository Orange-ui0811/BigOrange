package com.orangejd.server.ragb.retrieval;

/** Repository identity gets one constant boost; only content ranks order evidence within it. */
public final class RagBProjectEvidenceScorer {
    public static final String POLICY = "PROJECT_CONTENT_RRF_V1";
    private static final int RRF_K = 60;
    private RagBProjectEvidenceScorer() {}

    public static double score(String query, String title, String sourceRef, int vectorRank, int lexicalRank) {
        // A matching title/source alone discovers a project; it does not supply answer evidence.
        if (vectorRank <= 0 && lexicalRank <= 0) return 0;
        double content = reciprocal(vectorRank) + RagBFusionScorer.LEXICAL_WEIGHT * reciprocal(lexicalRank);
        return content + (RagBFusionScorer.exactNameMatch(query, title, sourceRef) ? 0.1 : 0);
    }

    private static double reciprocal(int rank) { return rank > 0 ? 1.0 / (RRF_K + rank) : 0; }
}
