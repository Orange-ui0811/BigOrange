package com.orangejd.server.customer;

import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerProjectsTest {
    @Test void noviceEnglishDifficultyAndUnknownDurationCanOutrankConfirmedAdvancedProjects() {
        var starter=card(); starter.put("type","BEGINNER_PROJECT");starter.put("difficulty","EASY");starter.remove("duration");
        var advanced=card();advanced.put("type","CAREER_PROJECT");advanced.put("difficulty","ADVANCED");advanced.put("quality",100);
        var conditions=Map.<String,Object>of("experience","刚学","goal",List.of("学习","求职"),"maxDurationDays",60);
        assertThat(((Number)service.ranking(starter,conditions,List.of()).get("total")).doubleValue()).isGreaterThan(((Number)service.ranking(advanced,conditions,List.of()).get("total")).doubleValue());
        assertThat(service.explorable(starter,conditions)).isTrue();assertThat(service.accepts(starter,conditions)).isFalse();
        assertThat(service.reason(advanced,conditions)).contains("明显门槛");
        assertThat(service.accepts(starter,Map.of("difficulty","入门"))).isTrue();
    }
    final CustomerProjects service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> card() { return new HashMap<>(Map.of("status","PUBLISHED","url","https://github.com/acme/demo","language","Java","name","Spring Demo","techStack","Spring","duration","1-2周")); }
    @Test void primaryLanguageHardFilterDoesNotUseMentionInDescription() {
        var p=card(); p.put("language","Python"); p.put("description","Java比较案例");
        assertThat(service.accepts(p,Map.of("language","Java"))).isFalse();
    }
    @Test void javaDoesNotMatchJavascript() { var p=card(); p.put("language","JavaScript"); assertThat(service.accepts(p,Map.of("language","Java"))).isFalse(); }
    @Test void unknownResourceDoesNotSatisfyExclusion() {
        assertThat(service.accepts(card(),Map.of("allowDocker",false))).isFalse();
        var p=card(); p.put("requiresDocker",false); assertThat(service.accepts(p,Map.of("allowDocker",false))).isTrue();
        p.put("requiresDocker",true); assertThat(service.accepts(p,Map.of("allowDocker",false))).isFalse();
    }
    @Test void unknownOrExcessiveDurationIsExcluded() {
        assertThat(service.accepts(card(),Map.of("maxDurationDays",7))).isFalse();
        assertThat(service.accepts(card(),Map.of("maxDurationDays",14))).isTrue();
        var p=card(); p.put("duration","每天2小时"); assertThat(service.accepts(p,Map.of("maxDurationDays",14))).isFalse();
    }
    @Test void inactiveArchivedAndUnsafeLinksAreExcluded() {
        var p=card(); p.put("status","HIDDEN"); assertThat(service.accepts(p,Map.of())).isFalse();
        p=card(); p.put("archived",true); assertThat(service.accepts(p,Map.of())).isFalse();
        p=card(); p.put("url","javascript:alert(1)"); assertThat(service.accepts(p,Map.of())).isFalse();
    }
    @Test void noSilentTopicRelaxation() { assertThat(service.accepts(card(),Map.of("topic","机器学习"))).isFalse(); }
    @Test void goalIsPreferenceButExplicitSourceTypeRemainsHard() {
        var p=card(); p.put("type","BEGINNER_PROJECT"); assertThat(service.accepts(p,Map.of("goal","学习"))).isTrue();
        assertThat(service.accepts(p,Map.of("goal","求职"))).isTrue();
        assertThat(service.accepts(p,Map.of("sourceType","CAREER_PROJECT"))).isFalse();
    }
    @Test void projectIdentifiersMustResolve() { assertThatThrownBy(()->service.detail("BEGINNER_PROJECT",99,Map.of())).hasMessage("PROJECT_NOT_FOUND"); }
    @Test void twoMonthsIsWithinHalfYearAndLongerProjectsAreExcluded() {
        var p=card(); p.put("duration","两个月");
        assertThat(service.accepts(p,Map.of("maxDurationDays",180))).isTrue();
        p.put("duration","七个月"); assertThat(service.explorable(p,Map.of("maxDurationDays",180))).isFalse();
    }
    @Test void missingFactsCanBeExploredButNeverClaimedAsMeetingHardConditions() {
        var p=card(); p.remove("duration"); var state=Map.<String,Object>of("maxDurationDays",180,"allowDocker",false);
        assertThat(service.explorable(p,state)).isTrue(); assertThat(service.accepts(p,state)).isFalse();
        assertThat(service.gaps(p,state)).hasSize(2);
        p.put("requiresDocker",true); assertThat(service.explorable(p,state)).isFalse();
        p.put("status","HIDDEN"); assertThat(service.explorable(p,Map.of())).isFalse();
    }
    @Test void explicitLowerBoundIsNotLostWhenAnUpperBoundAlsoExists() {
        var p=card(); var conditions=Map.<String,Object>of("minDurationDays",60,"maxDurationDays",180);
        p.put("duration","1-3个月"); assertThat(service.explorable(p,conditions)).isFalse();
        p.put("duration","2-3个月"); assertThat(service.accepts(p,conditions)).isTrue();
        p.put("duration","4-7个月"); assertThat(service.explorable(p,conditions)).isFalse();
    }
    @Test void semanticRankingAndBeginnerPreferenceAreAuditableAndCannotBypassHardFilters() {
        var related=card(); related.put("sourceRef","beginner:1"); related.put("quality",20); related.put("difficulty","初级");
        var unrelated=card(); unrelated.put("sourceRef","beginner:2"); unrelated.put("name","Other"); unrelated.put("quality",100);
        var hit=new com.orangejd.server.ragb.retrieval.RagBSearchHit(1,1,1,0,"Spring Demo","GITHUB","Spring Demo|snapshot","Intro",0,100,"evidence",.8,"HYBRID_RRF");
        var ranked=service.ranking(related,Map.of("experience","新手"),List.of(hit));
        var other=service.ranking(unrelated,Map.of("experience","新手"),List.of(hit));
        assertThat(((Number)ranked.get("total")).doubleValue()).isGreaterThan(((Number)other.get("total")).doubleValue());
        assertThat(((Number)ranked.get("preference")).doubleValue()).isPositive();
        related.put("language","Python"); assertThat(service.accepts(related,Map.of("language","Java"))).isFalse();
    }
}
