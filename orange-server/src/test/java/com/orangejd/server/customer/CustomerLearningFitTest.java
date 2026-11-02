package com.orangejd.server.customer;

import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerLearningFitTest {
    final CustomerProjects projects=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> card(long id,String name,String level){return new LinkedHashMap<>(Map.of("id",id,"type","CAREER_PROJECT","name",name,"sourceRef","career:"+id,"url","https://github.com/"+name,"status","PUBLISHED","language","Java","difficulty",level,"quality",100,"duration","7天"));}
    @ParameterizedTest @ValueSource(strings={"Java初学者","新手","刚学Java","零基础","Java beginner","novice"})
    void literalProfilesHaveConsistentBeginnerPreferenceWithoutChangingState(String profile){
        var state=new LinkedHashMap<String,Object>(Map.of("experience",profile));assertThat(CustomerLearningFit.novice(state)).isTrue();
        assertThat(CustomerLearningFit.assess(card(209,"alibaba/nacos","进阶"),state)).containsEntry("status","CHALLENGING").containsEntry("sourceText",profile);
        assertThat(state).containsOnlyKeys("experience");
    }
    @Test void negatedBeginnerIsNotSilentlyNormalizedToBeginner(){assertThat(CustomerLearningFit.novice(Map.of("experience","我不是初学者"))).isFalse();assertThat(CustomerLearningFit.novice(Map.of("experience","不再是新手"))).isFalse();}
    @Test void actualSelectionPrefersStarterBeforeConfirmedAdvancedNacosAndDoris(){
        var entry=card(7,"fixture/java-starter","入门");entry.remove("duration");
        var nacos=card(209,"alibaba/nacos","进阶");var doris=card(210,"apache/doris","进阶");
        var state=Map.<String,Object>of("experience","Java初学者","language","Java","maxDurationDays",14);
        var selected=projects.select(List.of(nacos,doris,entry),state,List.of());
        assertThat(selected.getFirst()).containsEntry("name","fixture/java-starter");assertThat(selected).hasSize(3);
        assertThat(CustomerWorkflow.map(selected.get(1).get("learningFit"))).containsEntry("status","CHALLENGING");
        assertThat(state).doesNotContainKeys("sourceType","difficulty");
    }
    @Test void beginnerPreferenceCannotOverrideExplicitLanguageConstraint(){
        var starter=card(7,"fixture/python-starter","入门");starter.put("language","Python");
        var advanced=card(209,"alibaba/nacos","进阶");
        assertThat(projects.select(List.of(starter,advanced),Map.of("experience","Java初学者","language","Java"),List.of())).extracting(p->p.get("name")).containsExactly("alibaba/nacos");
    }
    @Test void absentDifficultyDoesNotInventBeginnerSuitability(){var project=card(7,"fixture/unknown","入门");project.remove("difficulty");assertThat(CustomerLearningFit.assess(project,Map.of("experience","初学者"))).containsEntry("status","UNKNOWN");}
    @Test void largeCatalogEntriesCannotClaimBeginnerFitEvenWhenLabeledEntryLevel(){
        var nacos=card(209,"alibaba/nacos","入门");nacos.put("codebaseSize","LARGE");
        var fit=CustomerLearningFit.assess(nacos,Map.of("experience","Java初学者"));
        assertThat(fit).containsEntry("status","SCOPE_UNVERIFIED").containsEntry("catalogDifficulty","入门");
        assertThat(CustomerLearningFit.uncertain(fit)).isTrue();
        assertThat(projects.reason(nacos,Map.of("experience","Java初学者"))).contains("不能据此认定适合初学者").doesNotContain("与当前基础更接近");
    }
    @Test void realCatalogStyleLargeEntryLabelsDoNotOutrankSmallOrUnknownCandidates(){
        var nacos=card(209,"alibaba/nacos","入门");nacos.put("codebaseSize","LARGE");nacos.put("evidenceAvailable",true);
        var doris=card(210,"apache/doris","入门");doris.put("codebaseSize","LARGE");doris.put("evidenceAvailable",true);
        var entry=card(7,"fixture/java-starter","入门");entry.put("codebaseSize","SMALL");
        var unknown=card(8,"fixture/unknown"," ");unknown.remove("difficulty");
        assertThat(projects.select(List.of(nacos,doris,entry,unknown),Map.of("experience","Java初学者"),List.of())).extracting(p->p.get("name")).startsWith("fixture/java-starter","fixture/unknown");
    }
}
