package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;

class CustomerDiagnosticsTest {
    final CustomerProjects service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> card(int id,String language,String duration) {
        var card=new HashMap<String,Object>(Map.of("id",id,"sourceRef","career:"+id,"name","repo/"+id,"type","CAREER_PROJECT","status","PUBLISHED","url","https://github.com/repo/"+id,"language",language));
        if(duration!=null)card.put("duration",duration);return card;
    }
    @Test void countsAllCandidatesNotTopThreeAndSeparatesUnknownWithoutChangingState() {
        List<Map<String,Object>> pool=new ArrayList<>();for(int i=1;i<=7;i++)pool.add(card(i,"Java","2个月"));
        pool.add(card(8,"Java",null));pool.add(card(9,"Python","2周"));
        var state=new HashMap<String,Object>(Map.of("goal","学习","language","Java","maxDurationDays",30));
        var result=CustomerDiagnostics.evaluate(service,pool,state);
        assertThat((Map<?,?>)result.get("current")).isEqualTo(Map.of("confirmed",0,"uncertain",1,"total",1));
        var options=(List<Map<String,Object>>)result.get("alternatives");
        var duration=options.stream().filter(o->((Map<?,?>)o.get("changes")).containsKey("maxDurationDays")).findFirst().orElseThrow();
        assertThat(duration).containsEntry("confirmed",7).containsEntry("uncertain",1);
        assertThat((Map<?,?>)duration.get("changes")).isEqualTo(Map.of("maxDurationDays",60));
        assertThat(state).containsEntry("maxDurationDays",30).containsEntry("language","Java");
        assertThat(CustomerDiagnostics.describe(result)).contains("60天","尚未修改条件").doesNotContain("7 个","候选共","仓库去重");
    }
    @Test void duplicateInactiveAndArchivedCannotInflateCounts() {
        var p=card(1,"Java","2周");var duplicate=new HashMap<>(p);duplicate.put("type","BEGINNER_PROJECT");
        var inactive=card(2,"Java","2周");inactive.put("status","INVALID");
        var archived=card(3,"Java","2周");archived.put("archived",true);
        var result=CustomerDiagnostics.evaluate(service,List.of(p,duplicate,inactive,archived),Map.of("language","Java"));
        assertThat(result.get("current")).isEqualTo(Map.of("confirmed",1,"uncertain",0,"total",1));
    }
    @Test void pairRelaxationMustNameBothConditionsAndNotChangeOriginal() {
        var state=Map.<String,Object>of("language","Python","maxDurationDays",7);
        var result=CustomerDiagnostics.evaluate(service,List.of(card(1,"Java","2个月")),state);
        var options=(List<Map<String,Object>>)result.get("alternatives");
        assertThat(options).hasSize(1);assertThat(((Map<?,?>)options.getFirst().get("changes"))).hasSize(2);
        assertThat(options.getFirst().get("label").toString()).contains("并且");
    }
    @Test void deletingUnknownDoesNotCountAsConfirmedUntilTheUserApprovesThatChange() {
        var result=CustomerDiagnostics.evaluate(service,List.of(card(1,"Java",null)),Map.of("maxDurationDays",30));
        assertThat(result.get("current")).isEqualTo(Map.of("confirmed",0,"uncertain",1,"total",1));
        assertThat(((List<Map<String,Object>>)result.get("alternatives")).getFirst()).containsEntry("confirmed",1);
    }
    @Test void unrelatedTopicIsNeverSuggestedJustBecauseTheCatalogHasMoreProjects() {
        var p=card(1,"Python","2周");p.put("description","RAG knowledge base application");
        var state=Map.<String,Object>of("topic",List.of("量子芯片"),"language","Python");
        var result=CustomerDiagnostics.evaluate(service,List.of(p),state);
        var options=(List<Map<String,Object>>)result.get("alternatives");
        assertThat(options).isEmpty();
        assertThat(CustomerDiagnostics.describe(result)).contains("当前方向还没有有效的核对结果").doesNotContain("RAG","1 个","0 个","快照","候选共");
        assertThat(state).containsEntry("topic",List.of("量子芯片"));
    }
}
