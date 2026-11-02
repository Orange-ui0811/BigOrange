package com.orangejd.server.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local corpus snapshot; zero model/embedding calls. Does not claim end-to-end provider quality. */
class CustomerPurposeCorpusProbeTest {
    @Test @EnabledIfSystemProperty(named="customer.purpose.fixture",matches=".+")
    void actualCorpusSupportsColdStartWithoutReviewAndKeepsNarrowRequestsUnapproved() throws Exception {
        var mapper=new ObjectMapper();var json=new CustomerJson(mapper);
        var data=mapper.readValue(Files.readString(Path.of(System.getProperty("customer.purpose.fixture"))),new TypeReference<Map<String,Object>>(){});
        var documents=(List<Map<String,Object>>)data.get("documents");var catalog=mock(CustomerCatalogMapper.class);
        when(catalog.beginners()).thenReturn(List.of());when(catalog.careers()).thenReturn(List.of());when(catalog.externalDocuments()).thenReturn(documents);
        when(catalog.evidenceSources()).thenReturn(documents.stream().map(d->d.get("source_ref").toString()).toList());
        var service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher(),catalog);
        var pc=CustomerPurposeCatalog.bundled();var state=new LinkedHashMap<String,Object>(Map.of("topic",List.of("AI应用开发"),"language","Python","goal","求职","maxDurationDays",180,"provenance",Map.of("topic",Map.of("quote","可以用于AI应用开发"))));
        var pool=service.pool();var candidates=service.purposeCandidates(pool,state,List.of(),"AI应用开发 | AI application development");
        var ids=pc.seed(candidates,state,"offline-probe|"+pc.version(),json);
        assertThat(ids).hasSizeGreaterThanOrEqualTo(3);assertThat(CustomerPurposeMemory.restore(candidates,state,"offline-probe|"+pc.version())).isTrue();
        var selected=service.select(candidates,state,List.of());assertThat(selected).hasSize(3);
        assertThat(selected).allMatch(p->Objects.toString(p.get("url")).startsWith("https://github.com/"));
        var narrow=new LinkedHashMap<>(state);narrow.put("provenance",Map.of("topic",Map.of("quote","医院影像诊断的AI应用开发")));
        assertThat(pool.stream().filter(p->!pc.proof(p,narrow).isEmpty())).isEmpty();
        var report=new LinkedHashMap<String,Object>();report.put("scope","Current public GITHUB corpus; real Java filtering and source checks, no ANN/model/generation calls; NOT live quality or latency acceptance");
        report.put("documents",documents.size());report.put("eligiblePool",pool.size());report.put("nominated",candidates.size());report.put("sourceEvidenceIds",ids);report.put("selected",selected.stream().map(p->Map.of("name",p.get("name"),"matchStatus",p.get("matchStatus"),"conditionGaps",p.get("conditionGaps"))).toList());report.put("modelCalls",0);report.put("narrowScenarioNotAutoApproved",true);
        String output=System.getProperty("customer.purpose.report");if(output!=null) {var path=Path.of(output);Files.createDirectories(path.getParent());Files.writeString(path,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));}
    }
}
