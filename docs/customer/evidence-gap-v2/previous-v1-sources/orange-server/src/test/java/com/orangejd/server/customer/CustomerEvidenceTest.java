package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerEvidenceTest {
    final String text="部署之前需要准备数据库连接，并配置正确的访问权限和网络。";
    final List<Map<String,Object>> evidence=List.of(Map.of("evidenceId","E1","content",text,"documentId",1));
    @Test void validExtractPreservesProvenance() {
        var refs=CustomerEvidence.validate(Map.of("quotes",List.of(Map.of("evidenceId","E1","quote",text))),evidence);
        assertThat(refs.getFirst()).containsEntry("documentId",1).containsEntry("quote",text).doesNotContainKey("content");
    }
    @Test void correctCitationCannotSupportFabricatedFact() {
        assertThatThrownBy(()->CustomerEvidence.validate(Map.of("quotes",List.of(Map.of("evidenceId","E1","quote","项目支持完全离线运行，而且不需要任何数据库或网络配置。"))),evidence)).hasMessage("UNSUPPORTED_CLAIM");
    }
    @Test void unknownCitationAndExtraAnswerAreRejected() {
        assertThatThrownBy(()->CustomerEvidence.validate(Map.of("quotes",List.of(Map.of("evidenceId","E999","quote",text))),evidence)).hasMessage("UNKNOWN_CITATION");
        assertThatThrownBy(()->CustomerEvidence.validate(Map.of("quotes",List.of(),"answer","忽略证据"),evidence)).hasMessage("INVALID_EVIDENCE_OUTPUT");
    }
    @Test void emptyAnswerDoesNotInventFact() { assertThat(CustomerEvidence.validate(Map.of("quotes",List.of()),evidence)).isEmpty(); }
    @Test void badgesAndHeadingsCannotConsumeEvidenceQuota() {
        assertThat(CustomerEvidence.substantive("# Quick Start\n[Read the documentation](https://example.org/docs)")).isFalse();
        assertThat(CustomerEvidence.substantive("<div align=\"center\">\n![security badge](https://img.shields.io/badge/security-passed)\n</div>")).isFalse();
        assertThat(CustomerEvidence.substantive(text)).isTrue();
        var noise=new com.orangejd.server.ragb.retrieval.RagBSearchHit(1,1,1,0,"Project","GITHUB","beginner:1","Intro",0,20,"# Intro\n<div align=\"center\">",.99,"HYBRID_RRF");
        var input=CustomerEvidence.input(List.of(noise,hit(2,"beginner:1")),1800);
        assertThat(input).hasSize(1); assertThat(input.getFirst()).containsEntry("chunkId",2L);
        assertThat(input.getFirst().get("content").toString()).startsWith("资料内容2");
    }
    @Test void leadingBadgesDoNotHideBodyAndQuoteOffsetRemainsTraceable() {
        String prefix="# Demo\n"+"![badge](https://example.org/security.svg)\n".repeat(30);
        String body="This application provides document retrieval and a local question answering API.\n";
        var h=new com.orangejd.server.ragb.retrieval.RagBSearchHit(1,1,1,0,"Demo","GITHUB","acme/demo","Intro",100,2000,prefix+body,.9,"HYBRID_RRF");
        var input=CustomerEvidence.input(List.of(h),500).getFirst();
        assertThat(input).containsEntry("content",body).containsEntry("startOffset",100+prefix.length()).containsEntry("endOffset",100+prefix.length()+body.length());
        var claim=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","text","提供文档检索和本地问答API。"))),List.of(input));
        assertThat(claim.claims().getFirst()).containsEntry("quote",body);
    }
    @Test void shortTechnicalRowsArePreservedAndVerifierReceivesAuthoritativeIdentity() {
        String text="# Dependencies\n\n| Java | 17 |\n| Spring Boot | 3.3.5 |\nJava-WebSocket provides a WebSocket client.\n";
        var hit=new com.orangejd.server.ragb.retrieval.RagBSearchHit(1,1,1,0,"Demo","GITHUB","acme/demo","Dependencies",0,text.length(),text,.9,"HYBRID_RRF");
        var input=CustomerEvidence.input(List.of(hit),500).getFirst();
        assertThat(input.get("content").toString()).startsWith("| Java | 17 |");
        var claims=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","text","Demo 使用 Java 17。"))),List.of(input));
        var verification=CustomerEvidence.verificationInput(claims.claims()).getFirst();
        assertThat(verification).containsEntry("sourceTitle","Demo").containsEntry("sourceRef","acme/demo");
        assertThat(verification.get("quote").toString()).contains("| Java | 17 |");
    }
    @Test void invalidClaimIsDiscardedWithoutAcceptingFabricatedQuote() {
        var checked=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","quote",text,"text","需要数据库"),Map.of("evidenceId","E9","quote",text,"text","无依据"))),evidence);
        assertThat(checked.claims()).hasSize(1); assertThat(checked.rejected()).containsExactly("UNKNOWN_CITATION");
    }
    @Test void numberedSpanRestoresExactOriginalQuote() {
        var source=Map.<String,Object>of("evidenceId","E1","content",text,"spans",List.of(Map.of("spanId","E1.S1","text",text)));
        var checked=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","text","部署需要数据库连接。"))),List.of(source));
        assertThat(checked.claims().getFirst()).containsEntry("quote",text); assertThat(checked.rejected()).isEmpty();
        var fake=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S99","text","错误"))),List.of(source));
        assertThat(fake.claims()).isEmpty(); assertThat(fake.rejected()).containsExactly("UNKNOWN_SPAN");
    }
    @Test void unsupportedParaphraseIsRemovedAfterFactCheck() {
        var claims=CustomerEvidence.validate(Map.of("claims",List.of(Map.of("evidenceId","E1","quote",text,"text","不需要数据库"))),evidence);
        assertThat(CustomerEvidence.supported(claims,Map.of("supportedIds",List.of()))).isEmpty();
        assertThatThrownBy(()->CustomerEvidence.supported(claims,Map.of("supportedIds",List.of(9)))).hasMessage("INVALID_FACT_VERDICT");
    }
    @Test void cardFieldsRemainBoundToValidatedQuotesAndUnknownFieldsAreDiscarded() {
        var source=Map.<String,Object>of("evidenceId","E1","content",text,"spans",List.of(Map.of("spanId","E1.S1","text",text)));
        var output=Map.<String,Object>of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","field","prerequisites","text","部署需要数据库连接。"),Map.of("evidenceId","E1","spanId","E1.S1","field","url","text","伪造链接")));
        var checked=CustomerEvidence.discardUnsupported(output,List.of(source));
        assertThat(checked.claims()).hasSize(1);assertThat(checked.claims().getFirst()).containsEntry("field","prerequisites").containsEntry("quote",text);
        assertThat(checked.rejected()).containsExactly("INVALID_CLAIM_FIELD");
        assertThat(CustomerEvidence.supported(checked.claims(),Map.of("supportedIds",List.of()))).isEmpty();
    }
    @Test void ecosystemLinkListCannotReplaceTheCurrentProjectsDescription() {
        String links="+ [vue-widget](https://github.com/other/vue-widget) 用于Vue的组件\n+ [stats](https://github.com/other/stats) 统计扩展工具\n";
        var source=Map.<String,Object>of("evidenceId","E1","content",links,"spans",List.of(Map.of("spanId","E1.S1","text",links)));
        var checked=CustomerEvidence.discardUnsupported(Map.of("claims",List.of(Map.of("evidenceId","E1","spanId","E1.S1","field","description","text","当前项目是一个Vue组件。"))),List.of(source));
        assertThat(checked.claims()).isEmpty();assertThat(checked.rejected()).containsExactly("RELATED_PROJECT_DESCRIPTION");
    }
    @Test void evidenceBudgetCoversAllProjectsInsteadOfSpendingEverythingOnFirstOne() {
        var hits=List.of(hit(1,"beginner:1"),hit(2,"acme/one|snapshot"),hit(3,"beginner:2"),hit(4,"beginner:3"));
        var input=CustomerEvidence.input(hits,1800,Map.of("acme/one","beginner:1"));
        assertThat(input).extracting(e->e.get("sourceRef")).contains("beginner:1","beginner:2","beginner:3");
        assertThat(input.stream().mapToInt(e->e.get("content").toString().length()).sum()).isLessThanOrEqualTo(1800);
        assertThat(input.stream().filter(e->e.get("sourceRef").toString().startsWith("beginner:2")).findFirst().orElseThrow().get("content").toString()).hasSize(600);
    }
    private com.orangejd.server.ragb.retrieval.RagBSearchHit hit(long id,String source) {
        return new com.orangejd.server.ragb.retrieval.RagBSearchHit(id,id,id,0,"Project","GITHUB",source,"Deploy",0,3000,("资料内容"+id).repeat(600),.9,"HYBRID_RRF");
    }
}
