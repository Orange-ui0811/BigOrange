package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerPurposeCatalogTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    static final String TEXT="该框架支持搭建智能体，提供工具调用、任务编排和上下文管理。";
    Map<String,Object> card(int id) {
        var p=new LinkedHashMap<String,Object>();
        p.putAll(Map.of("id",id,"type","BEGINNER_PROJECT","name","demo/app"+id,"url","https://github.com/demo/app"+id,"sourceRef","beginner:"+id,"status","PUBLISHED","language","Python","duration","2个月","description",TEXT));
        return p;
    }
    CustomerPurposeCatalog catalog(int count) {
        List<Map<String,Object>> facts=new ArrayList<>();
        for(int id=1;id<=count;id++)facts.add(Map.of("id","test-"+id,"repository","https://github.com/demo/app"+id,"field","description","sourceHash",CustomerStore.hash(TEXT),"quote",TEXT,"relations",Map.of("ai-application","SUBTOPIC","agent","DIRECT")));
        return new CustomerPurposeCatalog(facts,"test-v1");
    }
    Map<String,Object> state() { return new LinkedHashMap<>(Map.of("topic",List.of("AI应用开发"),"language","Python","maxDurationDays",180)); }
    @Test void freshSessionsUsePublicFactsWithoutSharingChatOrProfile() {
        var pc=catalog(1);var p=card(1);
        for(int i=0;i<2;i++) {
            var s=state();assertThat(pc.seed(List.of(p),s,"policy",json)).containsExactly("test-1");
            assertThat(CustomerPurposeMemory.restore(List.of(p),s,"policy")).isTrue();
            assertThat(CustomerPurpose.matches(p,s)).isTrue();
        }
    }
    @Test void repositoryTextAndQuoteAreAllRequiredAndVersionsCannotReuseSessionProof() {
        var pc=catalog(1);var p=card(1);var s=state();pc.seed(List.of(p),s,"policy",json);
        p.put("description",TEXT+"该功能已移除。");assertThat(pc.proof(p,s)).isEmpty();assertThat(CustomerPurposeMemory.pending(List.of(p),s,"policy")).containsExactly(p);
        p.put("description",TEXT);p.put("url","https://github.com/other/app");assertThat(pc.proof(p,s)).isEmpty();
        p.put("url","https://github.com/demo/app1");p.put("versionId",2);assertThat(CustomerPurposeMemory.pending(List.of(p),s,"policy")).containsExactly(p);
        assertThat(CustomerPurposeMemory.pending(List.of(p),s,"policy|catalog:changed")).containsExactly(p);
    }
    @Test void specificObjectsNegationAndCombinedTopicsNeverInheritBroadDomainApproval() {
        var pc=catalog(1);var p=card(1);var s=state();
        for(String original:List.of("医院诊断的AI应用开发","AI应用开发但不要智能体","必须离线AI应用开发","AI应用开发并且需要语音识别")) {
            s.put("provenance",Map.of("topic",Map.of("quote",original)));assertThat(pc.proof(p,s)).isEmpty();
        }
        s.put("provenance",Map.of("topic",Map.of("quote","可以用于AI应用开发")));assertThat(pc.proof(p,s)).isNotEmpty();
        s.put("topic",List.of("AI应用开发","库存管理"));assertThat(pc.proof(p,s)).isEmpty();
        s.put("topic",List.of("RAG"));s.remove("provenance");assertThat(pc.proof(p,s)).isEmpty();
    }
    @Test void nominationFindsEvidenceBeyondAnnWindowButDoesNotWaiveConstraints() {
        var pc=catalog(1);var service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher(),null,pc);
        var p=card(1);var s=state();
        assertThat(service.purposeCandidates(List.of(p),s,List.of(),"unmatched query")).containsExactly(p);
        pc.seed(List.of(p),s,"policy",json);CustomerPurposeMemory.restore(List.of(p),s,"policy");
        assertThat(service.explorable(p,s)).isTrue();
        s.put("maxDurationDays",30);assertThat(service.explorable(p,s)).isFalse();
        s.put("maxDurationDays",180);s.put("language","Java");assertThat(service.purposeCandidates(List.of(p),s,List.of(),"unmatched query")).isEmpty();
        s.put("language","Python");p.put("status","HIDDEN");assertThat(service.explorable(p,s)).isFalse();
    }
    @Test void unknownFieldsStayUnknownAndUnapprovedCandidatesRemainPending() {
        var pc=catalog(1);var p=card(1);p.remove("language");var other=card(2);var s=state();
        pc.seed(List.of(p,other),s,"policy",json);assertThat(CustomerPurposeMemory.restore(List.of(p,other),s,"policy")).isTrue();
        assertThat(CustomerPurpose.status(s)).isEqualTo("PARTIAL_MATCHES");assertThat(CustomerPurposeMemory.pending(List.of(p,other),s,"policy")).containsExactly(other);
        var service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
        assertThat(service.gaps(p,s)).isNotEmpty();s.put("verificationMode","STRICT");assertThat(service.explorable(p,s)).isFalse();
    }
    @Test void bundledLedgerIsSmallAndContainsNoUserContext() {
        var pc=CustomerPurposeCatalog.bundled();assertThat(pc.version()).hasSize(64);
        assertThatThrownBy(()->new CustomerPurposeCatalog(List.of(Map.of("id","invalid")),"bad")).hasMessage("INVALID_PURPOSE_CATALOG");
    }
}
