package com.orangejd.server.ragb.retrieval;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagBFusionScorerTest {
    @Test
    void genericNameWordsCannotOvertakeSemanticEvidence() {
        double relevant = RagBFusionScorer.score("Which AI tool supports offline transfer?",
                "Optical transfer", "acme/optical-transfer", 10, 0, 0);
        double unrelated = RagBFusionScorer.score("Which AI tool supports offline transfer?",
                "AI workspace", "acme/workspace", 0, 1, 1);
        assertThat(relevant).isGreaterThan(unrelated);
    }

    @Test
    void explicitRepositoryNameStillBeatsGenericHighSimilarityHit() {
        double named = RagBFusionScorer.score("LangGraph如何恢复？", "LangGraph", "acme/langgraph|week", 20, 0, 2);
        double generic = RagBFusionScorer.score("LangGraph如何恢复？", "Generic agent", "acme/agent", 1, 1, 1);
        assertThat(named).isGreaterThan(generic);
    }

    @Test
    void latinNamesMustHaveBoundariesAndCanHaveChineseSuffixes() {
        assertThat(RagBFusionScorer.exactNameMatch("JavaScript项目", "Java", "acme/java")).isFalse();
        assertThat(RagBFusionScorer.exactNameMatch("Explain LangGraph framework", "LangGraph", "acme/langgraph")).isTrue();
        assertThat(RagBFusionScorer.exactNameMatch("LangGraph有哪些功能", "LangGraph", "acme/langgraph|week")).isTrue();
    }
}
