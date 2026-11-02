package com.orangejd.server.ragb.retrieval;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagBProjectEvidenceScorerTest {
    @Test void contentRelevantEvidenceBeatsRepositoryIntroductionDespiteLegacyNameOrdinal() {
        // The observed failure: name rank 2 used to defeat vector rank 2 in the same repository.
        double oldIntro=RagBFusionScorer.score("Docguard JSON 用法", "Docguard", "owner/Docguard", 6,1,2);
        double oldUsage=RagBFusionScorer.score("Docguard JSON 用法", "Docguard", "owner/Docguard", 2,10,8);
        assertThat(oldIntro).isGreaterThan(oldUsage);
        double intro=RagBProjectEvidenceScorer.score("Docguard JSON 用法", "Docguard", "owner/Docguard",6,1);
        double usage=RagBProjectEvidenceScorer.score("Docguard JSON 用法", "Docguard", "owner/Docguard",2,10);
        assertThat(usage).isGreaterThan(intro);
    }
    @Test void explicitRepositoryIdentityStillWinsOverAnUnrelatedHighSimilarityProject() {
        assertThat(RagBProjectEvidenceScorer.score("LangGraph如何恢复", "LangGraph", "acme/langgraph",20,0))
                .isGreaterThan(RagBProjectEvidenceScorer.score("LangGraph如何恢复", "Other agent", "acme/agent",1,1));
    }
    @Test void aNameOnlyMatchCannotBecomeAnswerEvidence() {
        assertThat(RagBProjectEvidenceScorer.score("Docguard有哪些功能", "Docguard", "acme/docguard",0,0)).isZero();
    }
    @Test void unnamedQueriesRetainExistingVectorAndLexicalScoring() {
        assertThat(RagBProjectEvidenceScorer.score("离线工具推荐", "LangGraph", "acme/langgraph",3,5))
                .isEqualTo(RagBFusionScorer.score("离线工具推荐", "LangGraph", "acme/langgraph",3,5,99));
    }
    @Test void comparingTwoExplicitlyNamedProjectsDoesNotGiveEitherAnArbitraryNameOrdinalAdvantage() {
        String question="比较 Flowise 与 Docguard 的用途";
        assertThat(RagBProjectEvidenceScorer.score(question,"Flowise","owner/Flowise",2,3))
                .isEqualTo(RagBProjectEvidenceScorer.score(question,"Docguard","owner/Docguard",2,3));
    }
}
