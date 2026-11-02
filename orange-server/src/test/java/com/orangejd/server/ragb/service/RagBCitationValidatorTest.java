package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagBCitationValidatorTest {
    private final RagBCitationValidator validator = new RagBCitationValidator();

    @Test
    void acceptsSentenceSupportedByExistingCitation() {
        var result = validator.validate("项目使用 Java 和 Spring Boot。[C1]", List.of(evidence()));
        assertThat(result.valid()).isTrue();
        assertThat(result.supportedSentences()).isEqualTo(1);
    }

    @Test
    void rejectsMissingAndUnknownCitations() {
        var missing = validator.validate("项目使用 Java 和 Spring Boot。", List.of(evidence()));
        var unknown = validator.validate("项目使用 Java 和 Spring Boot。[C9]", List.of(evidence()));
        assertThat(missing.valid()).isFalse();
        assertThat(missing.sentences().getFirst().reason()).isEqualTo("MISSING_CITATION");
        assertThat(unknown.valid()).isFalse();
        assertThat(unknown.sentences().getFirst().reason()).isEqualTo("UNKNOWN_CITATION");
    }

    @Test
    void rejectsCitationWhoseTextDoesNotSupportClaim() {
        var result = validator.validate("项目必须购买商业 GPU 集群。[C1]", List.of(evidence()));
        assertThat(result.valid()).isFalse();
        assertThat(result.sentences().getFirst().reason()).isEqualTo("LOW_TEXTUAL_SUPPORT");
    }

    private RagBSearchHit evidence() {
        String content = "项目使用 Java 和 Spring Boot，支持 Docker 部署。";
        return new RagBSearchHit(1, 2, 3, 0, "Demo", "GITHUB", "acme/demo", "部署",
                10, 10 + content.length(), content, 0.8, "VECTOR_EXACT");
    }
}
