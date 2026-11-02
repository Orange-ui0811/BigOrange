package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSourceFactServiceTest {
    final CustomerSourceFactMapper db=mock(CustomerSourceFactMapper.class);
    final CustomerProjects projects=mock(CustomerProjects.class);
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerSourceFactService service=new CustomerSourceFactService(db,json,projects);
    Map<String,Object> project() {return Map.of("type","EXTERNAL_PROJECT","id",1,"url","https://github.com/demo/app","description","This RAG application provides evidence based answers for enterprise documents.","versionId",2);}
    @Test void extractionIsOnlyADraftWithExactQuoteAndSourceVersion() {
        var fact=CustomerFactRefresh.proposal(project());when(db.insert(anyString(),anyString(),eq("DRAFT"))).thenReturn(1);
        service.propose(fact);
        assertThat(fact).containsEntry("reviewMethod","UNREVIEWED_EXTRACT");
        assertThat(CustomerSourceFactService.current(fact,project())).isTrue();
        verify(db).event(eq(fact.get("id").toString()),eq(1L),eq("DRAFT"),isNull(),anyString());
        verify(db,never()).transition(anyString(),anyLong(),eq("REVIEWED"),any());
    }
    @Test void staleSourceCannotBeApprovedAndMatchingSourceRecordsReviewer() {
        var fact=CustomerFactRefresh.proposal(project());String id=fact.get("id").toString();
        when(db.lock(id)).thenReturn(Map.of("revision",1,"fact_json",json.write(fact)));
        when(projects.pool()).thenReturn(List.of());
        assertThatThrownBy(()->service.review(id,1,"REVIEWED",5,"核对原文及用途")).hasMessage("FACT_SOURCE_STALE");
        when(projects.pool()).thenReturn(List.of(project()));when(db.transition(id,1,"REVIEWED",5L)).thenReturn(1);
        service.review(id,1,"REVIEWED",5,"核对原文及用途");verify(db).event(id,2,"REVIEWED",5L,"核对原文及用途");
        assertThatThrownBy(()->service.review(id,2,"REVIEWED",5,"核对")).hasMessage("FACT_REVISION_CONFLICT");
    }
    @Test void revokedAndDraftFactsCannotSeedAndBootstrapDoesNotOverwriteRevocation() {
        var fact=CustomerFactRefresh.proposal(project());
        when(db.all()).thenReturn(List.of(Map.of("id",fact.get("id"),"revision",2,"status","REVOKED","fact_json",json.write(fact))));
        var catalog=new CustomerPurposeCatalog(json,db);
        assertThat(catalog.proof(project(),Map.of("topic",List.of("RAG")))).isEmpty();
        catalog.seedDatabase();verify(db,never()).transition(anyString(),anyLong(),anyString(),any());
        when(db.all()).thenReturn(List.of(Map.of("id",fact.get("id"),"revision",3,"status","REVIEWED","fact_json",json.write(fact))));
        String approved=catalog.version();assertThat(catalog.proof(project(),Map.of("topic",List.of("RAG")))).isNotEmpty();
        when(db.all()).thenReturn(List.of());assertThat(catalog.version()).isNotEqualTo(approved);
    }
    @Test void updateInvalidatesProofImmediatelyBeforeRefreshJobRuns() {
        var p=new HashMap<>(project());var fact=CustomerFactRefresh.proposal(p);
        var catalog=new CustomerPurposeCatalog(List.of(fact),"test");p.put("versionId",3);
        assertThat(catalog.proof(p,Map.of("topic",List.of("RAG")))).isEmpty();
        when(db.all()).thenReturn(List.of(Map.of("id",fact.get("id"),"revision",1,"status","REVIEWED","fact_json",json.write(fact))));
        when(db.transition(anyString(),eq(1L),eq("STALE"),isNull())).thenReturn(1);
        assertThat(service.markStale(List.of(p))).isEqualTo(1);
    }
}
