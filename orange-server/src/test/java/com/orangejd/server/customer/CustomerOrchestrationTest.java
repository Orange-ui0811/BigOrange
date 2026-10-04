package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrchestrationTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerProjects projects=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> state() { return new LinkedHashMap<>(Map.of("topic",List.of("AI应用开发"),"language","Python","maxDurationDays",180)); }
    Map<String,Object> card(int id,String language) {
        return new LinkedHashMap<>(Map.of("id",id,"type","BEGINNER_PROJECT","name","demo/app"+id,"url","https://github.com/demo/app"+id,"sourceRef","beginner:"+id,"status","PUBLISHED","language",language,"duration","2个月","description","AI应用开发：用于检索知识并提供有出处的智能问答。","requiresDocker",false));
    }
    void approve(List<Map<String,Object>> cards,Map<String,Object> state) {
        var input=CustomerPurpose.input(cards,state,json,6000);
        var output=Map.<String,Object>of("matches",cards.stream().map(p->Map.of("key",CustomerPurpose.key(p),"relation","DIRECT","quote",p.get("description"))).toList());
        CustomerPurposeMemory.remember(output,input,cards,state,"model-v1");
    }
    @Test void knownConflictsCannotCrowdOutThirteenthEligibleCandidate() {
        var state=state();List<Map<String,Object>> pool=new ArrayList<>();for(int i=1;i<=12;i++)pool.add(card(i,"Java"));pool.add(card(13,"Python"));
        var old=CustomerPurpose.nominate(pool,state,List.of(),"AI应用开发");
        var updated=projects.purposeCandidates(pool,state,List.of(),"AI应用开发");
        assertThat(old).hasSize(12).noneMatch(p->"Python".equals(p.get("language")));
        assertThat(updated).hasSize(1);assertThat(updated.getFirst().get("id")).isEqualTo(13);
    }
    @Test void unknownFieldsRemainUnknownAndStrictModeCannotAuthorizeThem() {
        var unknown=card(1,"Python");unknown.remove("language");unknown.remove("duration");var state=state();
        approve(List.of(unknown),state);CustomerPurposeMemory.restore(List.of(unknown),state,"model-v1");
        assertThat(projects.select(List.of(unknown),state,List.of())).hasSize(1);
        assertThat(unknown).containsEntry("matchStatus","NEEDS_VERIFICATION");
        state.put("verificationMode","STRICT");assertThat(projects.select(List.of(unknown),state,List.of())).isEmpty();
    }
    @Test void counterfactualPoolCannotBecomeARecommendationAndStillSupportsAnAdjustment() {
        var java=card(1,"Java");var state=state();var nominated=projects.purposeCandidates(List.of(java),state,List.of(),"AI应用开发");
        approve(nominated,state);CustomerPurposeMemory.restore(nominated,state,"model-v1");
        assertThat(projects.select(nominated,state,List.of())).isEmpty();
        assertThat(CustomerDiagnostics.describe(CustomerDiagnostics.evaluate(projects,List.of(java),state))).contains("Java","尚未修改条件");
        assertThat(state.get("language")).isEqualTo("Python");
    }
    @Test void reuseDecisionAfterDurationChangeButRecheckTheActualLimit() {
        var state=state();var p=card(1,"Python");approve(List.of(p),state);
        state.put("maxDurationDays",30);
        assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).isEmpty();
        CustomerPurposeMemory.restore(List.of(p),state,"model-v1");
        assertThat(projects.select(List.of(p),state,List.of())).isEmpty();
    }
    @Test void topicOriginalRequestModelIdentityAndSourceChangesInvalidateCache() {
        var state=state();var p=card(1,"Python");approve(List.of(p),state);
        assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v2")).containsExactly(p);
        state.put("topic",List.of("库存管理"));assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).containsExactly(p);
        state.put("topic",List.of("AI应用开发"));state.put("provenance",Map.of("topic",Map.of("quote","必须离线的AI应用开发")));
        assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).containsExactly(p);state.remove("provenance");
        p.put("versionId",2);assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).containsExactly(p);p.remove("versionId");
        p.put("description","已改为其他用途");assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).containsExactly(p);
    }
    @Test void successfulNegativeDecisionIsCachedButUnsubmittedCandidateIsNot() {
        var state=state();var a=card(1,"Python");var b=card(2,"Python");
        var input=CustomerPurpose.input(List.of(a),state,json,6000);
        CustomerPurposeMemory.remember(Map.of("matches",List.of()),input,List.of(a,b),state,"model-v1");
        assertThat(CustomerPurposeMemory.pending(List.of(a,b),state,"model-v1")).containsExactly(b);
        assertThat(CustomerPurposeMemory.restore(List.of(a,b),state,"model-v1")).isFalse();
        assertThat(CustomerPurposeMemory.restore(List.of(a),state,"model-v1")).isTrue();assertThat(CustomerPurpose.status(state)).isEqualTo("SUCCEEDED_EMPTY");
    }
    @Test void partialRecoveryKeepsOnlyFreshProofsAndCannotAssertNoMatch() {
        var state=state();var a=card(1,"Python");var b=card(2,"Python");approve(List.of(a),state);
        CustomerPurpose.failed(state,new IllegalStateException("PROVIDER_UNAVAILABLE"));
        assertThat(CustomerPurposeMemory.restore(List.of(a,b),state,"model-v1")).isTrue();
        assertThat(CustomerPurpose.status(state)).isEqualTo("PARTIAL_MATCHES");
        assertThat(projects.select(List.of(a,b),state,List.of())).containsExactly(a);
        assertThat(CustomerDiagnostics.evaluate(projects,List.of(a,b),state)).containsEntry("status","RELEVANCE_UNAVAILABLE");
        a.put("archived",true);assertThat(projects.select(List.of(a,b),state,List.of())).isEmpty();
    }
    @Test void planIsReusableOnlyForSameSemanticScopeAndModel() {
        var state=state();CustomerPurposeMemory.rememberPlan(state,"model-v1","AI应用开发 | RAG");
        state.put("maxDurationDays",30);assertThat(CustomerPurposeMemory.plannedQuery(state,"model-v1")).isEqualTo("AI应用开发 | RAG");
        assertThat(CustomerPurposeMemory.plannedQuery(state,"model-v2")).isNull();
        state.put("topic",List.of("库存管理"));assertThat(CustomerPurposeMemory.plannedQuery(state,"model-v1")).isNull();
    }
    @Test void cacheIsBoundedAndExpiredEntriesRequireReview() {
        var state=state();for(int i=1;i<=55;i++)approve(List.of(card(i,"Python")),state);
        var memory=(Map<String,Object>)state.get(CustomerPurposeMemory.KEY);var entries=(Map<String,Object>)memory.get("entries");assertThat(entries).hasSize(48);
        var p=card(55,"Python");var expired=new LinkedHashMap<>((Map<String,Object>)entries.get(CustomerPurpose.key(p)));expired.put("at",Instant.now().minusSeconds(1801).toString());entries.put(CustomerPurpose.key(p),expired);
        assertThat(CustomerPurposeMemory.pending(List.of(p),state,"model-v1")).containsExactly(p);
    }
    @Test void interruptedCheckpointIsRecoveredFromOwnedRunWithoutReplayingAnything() {
        var state=state();var db=mock(CustomerMapper.class);CustomerTaskProgress.advance(state,"old","REVIEWING");
        when(db.run("old")).thenReturn(Map.of("session_id",1,"status","INTERRUPTED"));
        CustomerTaskProgress.recover(state,db,1,"new");
        assertThat(CustomerTaskProgress.visible(state)).containsEntry("phase","REVIEWING").containsEntry("status","INTERRUPTED").doesNotContainKey("runId");
        assertThat(CustomerTaskProgress.describe(state)).contains("核对项目用途","已确认条件仍保留");
    }
    @Test void differentSessionCannotProvideRecoveryFacts() {
        var state=state();var db=mock(CustomerMapper.class);CustomerTaskProgress.advance(state,"old","REVIEWING");
        when(db.run("old")).thenReturn(Map.of("session_id",9,"status","INTERRUPTED"));CustomerTaskProgress.recover(state,db,1,"new");
        assertThat(CustomerTaskProgress.visible(state)).containsEntry("status","RUNNING");
    }
    @Test void cachedSingleBatchRetainsOrderButDifferentBatchRanksAreNotCompared() {
        var state=state();var a=card(1,"Python");var b=card(2,"Python");var c=card(3,"Python");
        var input=CustomerPurpose.input(List.of(a,b),state,json,6000);
        CustomerPurposeMemory.remember(Map.of("matches",List.of(Map.of("key",CustomerPurpose.key(b),"relation","DIRECT","quote",b.get("description")),Map.of("key",CustomerPurpose.key(a),"relation","DIRECT","quote",a.get("description")))),input,List.of(a,b),state,"model-v1");
        CustomerPurposeMemory.restore(List.of(a,b),state,"model-v1");assertThat(CustomerPurpose.rank(b,state)).isEqualTo(1);assertThat(CustomerPurpose.rank(a,state)).isEqualTo(2);
        approve(List.of(c),state);CustomerPurposeMemory.restore(List.of(a,b,c),state,"model-v1");
        assertThat(CustomerPurpose.rank(a,state)).isEqualTo(CustomerPurpose.rank(c,state));
    }
}
