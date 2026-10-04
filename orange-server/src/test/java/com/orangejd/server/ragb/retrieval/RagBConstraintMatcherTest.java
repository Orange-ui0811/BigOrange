package com.orangejd.server.ragb.retrieval;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagBConstraintMatcherTest {
    private final RagBConstraintMatcher matcher = new RagBConstraintMatcher();

    @Test
    void javaDoesNotMatchJavascript() {
        RagBSearchConstraints constraints = constraints(Set.of("Java"));

        assertThat(matcher.filterReason(Map.of("sourceType", "BEGINNER_PROJECT",
                "language", "JavaScript", "techStack", "Vue Node.js"), constraints))
                .isEqualTo("LANGUAGE_MISMATCH");
        assertThat(matcher.filterReason(Map.of("sourceType", "BEGINNER_PROJECT",
                "language", "Java", "techStack", "Spring Boot"), constraints)).isNull();
    }

    @Test
    void aliasesStillUseExactTokens() {
        assertThat(matcher.filterReason(Map.of("sourceType", "BEGINNER_PROJECT",
                        "language", "TypeScript", "techStack", "Vue.js"), constraints(Set.of("ts", "vue"))))
                .isNull();
    }

    private RagBSearchConstraints constraints(Set<String> languages) {
        return new RagBSearchConstraints(Set.of("BEGINNER_PROJECT"), languages, null, null,
                null, null, null, false, false);
    }
}
