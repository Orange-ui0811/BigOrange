package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagBRelevanceGuardTest {
    private final RagBRelevanceGuard guard = new RagBRelevanceGuard();
    @Test void genericFreeFeaturesAndUnreadLicensingLinksDoNotSupplyCommercialPrice(){
        for(String text:List.of("Most features are free, even for commercial use. Member's privileges can only be used after payment, please refer to [Pricing](https://example.org/pricing).",
                "The Community and Evaluation tiers are free to use; see [licensing](#license) for commercial use.","License: MIT. Feel free to contribute.","[Commercial license price $100](https://example.org/license)"))
            assertThat(guard.assess("商业授权价格是多少",List.of(hit(text))).relevant()).as(text).isFalse();
    }
    @Test void explicitLicencePricesAndCommercialFreeStatementsRemainUsable(){
        for(String text:List.of("Commercial license price is $10 per month.","商业许可证价格为每年100元。","The commercial license is free of charge.","Commercial use is free."))
            assertThat(guard.assess("商业授权价格是多少",List.of(hit(text))).relevant()).as(text).isTrue();
    }

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
