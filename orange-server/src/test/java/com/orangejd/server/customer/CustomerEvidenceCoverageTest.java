package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerEvidenceCoverageTest {
    private final String question="Flowise 的最低内存和安装命令是什么？";
    @Test void noSupportedClaimCannotProduceCompleteCoverage() {
        var result=CustomerEvidenceCoverage.assess(question,Map.of("supportedIds",List.of(),"coverage","COMPLETE","missingFacts",List.of()),0);
        assertThat(result.status()).isEqualTo("INSUFFICIENT");
    }
    @Test void trueCitationWithoutCoverageIsUnassessed() {
        var result=CustomerEvidenceCoverage.assess(question,Map.of("supportedIds",List.of(0)),1);
        assertThat(result.status()).isEqualTo("UNASSESSED");assertThat(result.missingFacts()).hasSize(1);
    }
    @Test void partialAnswerPreservesTheMissingFactInsteadOfAskingTheUserToCertifyIt() {
        var result=CustomerEvidenceCoverage.assess(question,Map.of("supportedIds",List.of(0),"coverage","PARTIAL","missingFacts",List.of(
                Map.of("questionQuote","最低内存","reason","只有安装命令，没有最低机器内存要求"))),1);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.missingFacts().getFirst()).containsEntry("owner","PROJECT_FACT").containsEntry("text","最低内存");
    }
    @Test void fabricatedGapOrUserQuestionCannotBecomeAnAuthoritativeGap() {
        for(var row:List.of(Map.of("questionQuote","你的Java水平","reason","请补充"),Map.of("questionQuote","最低内存","reason","缺失","owner","USER_INPUT"))) {
            var result=CustomerEvidenceCoverage.assess(question,Map.of("supportedIds",List.of(0),"coverage","PARTIAL","missingFacts",List.of(row)),1);
            assertThat(result.status()).isEqualTo("UNASSESSED");
            assertThat(result.missingFacts().getFirst()).containsEntry("questionQuote",question).containsEntry("owner","PROJECT_FACT");
        }
    }
    @Test void completeWithAnExplicitGapIsDowngraded() {
        var result=CustomerEvidenceCoverage.assess(question,Map.of("supportedIds",List.of(0),"coverage","COMPLETE","missingFacts",List.of(
                Map.of("questionQuote","最低内存","reason","尚无依据"))),1);
        assertThat(result.status()).isEqualTo("PARTIAL");
    }
    @Test void completeSupportedNegativeFactDoesNotNeedMoreUserPreferences() {
        var result=CustomerEvidenceCoverage.assess("是否支持Windows？",Map.of("supportedIds",List.of(0),"coverage","COMPLETE","missingFacts",List.of()),1);
        assertThat(result.status()).isEqualTo("COMPLETE");assertThat(result.missingFacts()).isEmpty();
    }
    @Test void verificationPayloadIncludesTheQuestionOnlyForQa() {
        assertThat(CustomerEvidenceCoverage.payload(question,List.of(),false)).containsEntry("question",question);
        assertThat(CustomerEvidenceCoverage.payload(question,List.of(),true)).containsOnlyKeys("claims");
    }
}
