package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import com.orangejd.server.ragb.service.RagBSearchService;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSourceFirstConversationTest {
    final CustomerPurposeCatalogTest fixture=new CustomerPurposeCatalogTest();
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    @Test void coldRequestCanReturnThreeSourceBackedCardsWithoutAnyOnlinePurposeReview() { exercise(3,false); }
    @Test void recentRejectionStillAllowsOneIndependentSourceFactWithoutResubmittingTrimmedBatch() { exercise(1,true); }
    @Test void newReviewFailureKeepsOneIndependentSourceFact() { exercise(1,false); }
    void exercise(int known,boolean paused) {
        var store=mock(CustomerStore.class);var db=mock(CustomerMapper.class);var model=mock(CustomerModel.class);var tools=mock(CustomerTools.class);var context=mock(CustomerTools.Context.class);
        var pc=fixture.catalog(known);var state=fixture.state();var candidates=new ArrayList<Map<String,Object>>();for(int i=1;i<=4;i++)candidates.add(fixture.card(i));
        var projects=spy(new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher(),null,pc));
        if(paused) {
            var http=org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.BAD_REQUEST,"bad",org.springframework.http.HttpHeaders.EMPTY,"Content Exists Risk".getBytes(),java.nio.charset.StandardCharsets.UTF_8);
            CustomerPurpose.failed(state,AiToolProviderException.from(http),Map.of("topic","AI应用开发","originalRequest","AI应用开发","candidates",List.of()));
        }
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","r");
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","按当前条件查找项目"));
        when(store.session(1,5)).thenReturn(Map.of("state_json",json.write(state)));when(db.messages(1,Long.MAX_VALUE,40)).thenReturn(List.of());
        when(tools.context(eq("r"),eq(5L),any())).thenReturn(context);when(context.profile()).thenReturn("");
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        when(model.call(eq(CustomerPurpose.PLAN_PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("queries",List.of("AI application development")));
        when(model.call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList())).thenThrow(new IllegalStateException("PROVIDER_UNAVAILABLE"));
        when(context.projectRecall(anyString())).thenReturn(new RagBSearchService.SearchResult("test","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        when(context.purposeCandidates(anyMap(),anyList(),anyString())).thenReturn(candidates);
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("EVIDENCE_UNAVAILABLE"));
        doAnswer(i->fixture.card(((Long)i.getArgument(1)).intValue())).when(projects).detail(anyString(),anyLong(),anyMap());
        var app=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties(),pc);
        app.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var result=org.mockito.ArgumentCaptor.forClass(Map.class);verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        var value=result.getValue();assertThat(value).containsEntry("status","PROJECTS_WITH_LIMITATIONS").containsEntry("purposeReviewIncomplete",true);
        assertThat((List<?>)value.get("projects")).hasSize(known);
        for(Object raw:(List<?>)value.get("projects"))assertThat((Map<String,Object>)raw).containsEntry("importable",false);
        assertThat((Map<String,Object>)value.get("state")).containsEntry("language","Python").containsEntry("maxDurationDays",180);
        verify(model,times(known>=3 || paused?0:1)).call(eq(CustomerPurpose.REVIEW_PROMPT),anyMap(),any(),anyList());
        verify(db).step(eq("r"),eq("sourcePurposeEvidence"),eq("SUCCEEDED"),anyString());
    }
}
