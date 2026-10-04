package com.orangejd.server.customer;

import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerExternalProjectsTest {
    final CustomerCatalogMapper catalog=mock(CustomerCatalogMapper.class);
    final CustomerProjects service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher(),catalog);
    Map<String,Object> doc(long id,String ref) {return Map.of("id",id,"source_ref",ref,"title",ref,"active_version_id",id+100);}
    @Test void crawledRepositoryWithoutCatalogRowCanBeRecommendedAndTraced() {
        when(catalog.externalDocuments()).thenReturn(List.of(doc(1,"acme/demo|snapshot")));
        when(catalog.externalDocument(1)).thenReturn(doc(1,"acme/demo|snapshot"));
        var cards=service.search(Map.of("language","Java","maxDurationDays",180));
        assertThat(cards).hasSize(1);var c=cards.getFirst();
        assertThat(c).containsEntry("type","EXTERNAL_PROJECT").containsEntry("url","https://github.com/acme/demo").containsEntry("matchStatus","NEEDS_VERIFICATION");
        assertThat((List<?>)c.get("conditionGaps")).hasSize(2);
        assertThatThrownBy(()->service.detail("EXTERNAL_PROJECT",1,Map.of("language","Java"))).hasMessage("PROJECT_INACTIVE_OR_CONSTRAINT_MISMATCH");
        assertThat(service.exploreDetail("EXTERNAL_PROJECT",1,Map.of("language","Java"))).containsEntry("name","acme/demo");
        when(catalog.externalDocument(1)).thenReturn(null);
        assertThatThrownBy(()->service.exploreDetail("EXTERNAL_PROJECT",1,Map.of())).hasMessage("PROJECT_NOT_FOUND");
    }
    @Test void unpublishedProjectIsEligibleButDuplicateReadmeDoesNotCreateAnotherProject() {
        var p=new BeginnerProject();p.setId(1L);p.setFullName("acme/demo");p.setHtmlUrl("https://github.com/acme/demo");p.setStatus("CANDIDATE");p.setPrimaryLanguage("Java");p.setEstimatedDuration("2个月");
        when(catalog.beginners()).thenReturn(List.of(p));
        when(catalog.externalDocuments()).thenReturn(List.of(doc(2,"acme/demo"),doc(3,"acme/other"),doc(4,"acme/other|new")));
        var pool=service.pool();assertThat(pool).hasSize(2);
        assertThat(service.search(Map.of("language","Java","maxDurationDays",180)).getFirst()).containsEntry("status","CANDIDATE").containsEntry("matchStatus","CONFIRMED");
    }
    @Test void malformedSourceRefCannotGenerateRepositoryLink() {
        when(catalog.externalDocuments()).thenReturn(List.of(doc(1,"../../fake"),doc(2,"https://evil.test/repo"),doc(3,"topics/java"),doc(4,"../fake")));
        assertThat(service.search(Map.of())).isEmpty();
    }
    @Test void explicitIntroLanguageExcludesKnownConflictEvenInExploration() {
        var document=new HashMap<String,Object>(doc(1,"acme/scanner"));
        document.put("topic_text","# Scanner\n\n![Go](https://badge.example/go)\n\nA concurrent Go CLI that scans RAG documents.\n");
        when(catalog.externalDocuments()).thenReturn(List.of(document));
        assertThat(service.search(Map.of("language","Java","topic",List.of("RAG")))).isEmpty();
        var go=service.search(Map.of("language","Go","topic",List.of("RAG"))).getFirst();
        assertThat(go).containsEntry("language","Go").containsKey("languageEvidence");
        assertThat(CustomerSourceFacts.explicitLanguage("# Demo\n\nSupports Java clients and Python SDKs.\n")).isEmpty();
        assertThat(CustomerSourceFacts.explicitLanguage("# Demo\n\nInstall dependencies first.\n\nA Python library is used for testing.\n")).isEmpty();
    }
}
