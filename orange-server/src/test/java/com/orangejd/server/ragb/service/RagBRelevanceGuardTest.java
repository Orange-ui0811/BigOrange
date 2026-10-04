package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagBRelevanceGuardTest {
    private final RagBRelevanceGuard guard = new RagBRelevanceGuard();

    @Test
    void requiresActualPricingEvidenceForPricingQuestions() {
        assertThat(guard.assess("商业授权价格是多少", List.of(hit("MIT license"))).relevant()).isFalse();
        assertThat(guard.assess("商业授权价格是多少", List.of(hit("商业许可证价格为每年 100 元"))).relevant()).isTrue();
        assertThat(guard.assess("商业授权价格是多少", List.of(hit("MIT license"), hit("freely available book"))).relevant()).isFalse();
    }

    private RagBSearchHit hit(String content) {
        return new RagBSearchHit(1, 2, 3, 0, "title", "MANUAL", "manual:1",
                "overview", 0, content.length(), content, 0.8, "VECTOR_EXACT");
    }
}
