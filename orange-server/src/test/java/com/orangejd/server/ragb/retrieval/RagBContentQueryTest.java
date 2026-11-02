package com.orangejd.server.ragb.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RagBContentQueryTest {
    @Test void separatesProjectIdentityWhileKeepingTechnicalTerms() {
        assertThat(RagBContentQuery.terms("Flowise Docker Compose 部署",List.of("FlowiseAI/Flowise")))
                .containsExactly("Docker","Compose","部署");
    }
    @Test void retainsJsonAndCiInsteadOfDeletingAllLatinWords() {
        assertThat(RagBContentQuery.terms("Docguard JSON CI 用法",List.of("Docguard","owner/Docguard|snapshot")))
                .containsExactly("JSON","CI","用法");
    }
    @Test void noKnownExplicitProjectMeansNoChangeToTheContentQuery() {
        String q="JavaScript React 前端项目";
        assertThat(RagBContentQuery.terms(q,List.of("owner/java","owner/reactive"))).isEqualTo(RagBQueryTokenizer.tokenize(q));
    }
    @Test void comparisonsKeepBothProjectsInIdentityButNeitherInContentTerms() {
        assertThat(RagBContentQuery.terms("Flowise Docguard 功能比较",List.of("owner/Flowise","owner/Docguard")))
                .containsExactly("功能比较");
    }
    @Test void aProjectOnlyQueryHasNoInventedBodyKeyword() {
        assertThat(RagBContentQuery.terms("Flowise",List.of("Flowise"))).isEmpty();
    }
}
