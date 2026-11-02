package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.service.RagBSearchService;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.service.PersonaPromptContextService;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerToolsTest {
    CustomerMapper db=mock(CustomerMapper.class); CustomerProjects projects=mock(CustomerProjects.class);
    PersonaPromptContextService profiles=mock(PersonaPromptContextService.class); RagBMapper indexes=mock(RagBMapper.class);
    RagBSearchService knowledge=mock(RagBSearchService.class);
    CustomerProperties props=new CustomerProperties(); CustomerTools tools;
    @BeforeEach void setup() {
        props.setEnabled(true); props.setAllowedAccounts(Set.of(5L)); when(db.run("r")).thenReturn(Map.of("status","RUNNING"));
        when(profiles.confirmedContext(5L)).thenReturn("Java新手");
        tools=new CustomerTools(knowledge,projects,profiles,db,new CustomerJson(new ObjectMapper()),indexes,props);
    }
    @AfterEach void close() { tools.close(); }
    @Test void historyHasTwoReadsWithoutConsumingFiveBusinessToolSlots() {
        when(projects.search(anyMap())).thenReturn(List.of());
        var context=tools.context("r",5,Instant.now().plusSeconds(20));
        assertThat(context.contextRead("searchConversationHistory",Map.of("query","Java"),()->"{}")).isEqualTo("{}");
        assertThat(context.contextRead("readConversationSource",Map.of("messageId",1),()->"{}")).isEqualTo("{}");
        for(int i=0;i<5;i++)context.projects(Map.of("fixture",i));
        assertThat(context.calls).isEqualTo(7);
        assertThatThrownBy(()->context.contextRead("readConversationSource",Map.of("messageId",2),()->"{}")).hasMessage("TOOL_LIMIT");
    }
    @Test void historyReadChecksCancellationBeforeRunningCallback() {
        var context=tools.context("r",5,Instant.now().plusSeconds(20));
        when(db.run("r")).thenReturn(Map.of("status","CANCELLED"));
        var executed=new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(()->context.contextRead("searchConversationHistory",Map.of("query","Java"),()->{executed.set(true);return "{}";})).hasMessage("RUN_STOPPED");
        assertThat(executed).isFalse();
    }
    @Test void rapidTwoCallerHandoffsDoNotRejectAvailableCapacity() throws Exception {
        try(var callers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=callers.submit(()->{for(int i=0;i<40;i++)tools.context("r",5,Instant.now().plusSeconds(10)).profile();});
            var b=callers.submit(()->{for(int i=0;i<40;i++)tools.context("r",5,Instant.now().plusSeconds(10)).profile();});
            a.get(8,java.util.concurrent.TimeUnit.SECONDS);b.get(8,java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(profiles,times(80)).confirmedContext(5L);
    }
    @Test void cancelledQueuedCallNeverInvokesBusinessWork() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(2);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(profiles.confirmedContext(5L)).thenAnswer(inv->{entered.countDown();release.await(3,java.util.concurrent.TimeUnit.SECONDS);return "profile";});
        try(var callers=java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var a=callers.submit(()->tools.context("r",5,Instant.now().plusSeconds(5)).profile());
            var b=callers.submit(()->tools.context("r",5,Instant.now().plusSeconds(5)).profile());
            assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var queued=callers.submit(()->tools.context("r",5,Instant.now().plusMillis(100)).projects(Map.of()));
            assertThatThrownBy(()->queued.get(1,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
            release.countDown();a.get();b.get();
            verifyNoInteractions(projects);
        } finally {release.countDown();}
    }
    @Test void identicalToolsReuseResultAndServerInjectsOwner() {
        var context=tools.context("r",5,Instant.now().plusSeconds(90)); assertThat(context.profile()).isEqualTo(context.profile());
        verify(profiles,times(1)).confirmedContext(5L); verify(db,times(1)).step(eq("r"),eq("getConfirmedProfile"),eq("SUCCEEDED"),anyString());
    }
    @Test void sixthDistinctBusinessCallIsBlocked() {
        var context=tools.context("r",5,Instant.now().plusSeconds(90));
        for(int i=0;i<5;i++) context.projects(Map.of("topic","topic"+i));
        assertThatThrownBy(()->context.projects(Map.of("topic","sixth"))).hasMessage("TOOL_LIMIT"); verify(projects,times(5)).search(anyMap());
    }
    @Test void deadlineCancellationAndDisabledAccountPreventTools() {
        props.setAllAuthenticatedUsers(false);
        assertThatThrownBy(()->tools.context("r",5,Instant.now().minusSeconds(1)).profile()).hasMessage("DEADLINE_EXCEEDED");
        when(db.run("r")).thenReturn(Map.of("status","CANCELLED")); assertThatThrownBy(()->tools.context("r",5,Instant.now().plusSeconds(90)).profile()).hasMessage("RUN_STOPPED");
        assertThatThrownBy(()->tools.context("r",6,Instant.now().plusSeconds(90)).profile()).hasMessage("CUSTOMER_DISABLED"); verifyNoInteractions(profiles);
    }
    @Test void publicAccessPermitsToolsForNonWhitelistedAccountAndInjectsItsOwner() {
        when(profiles.confirmedContext(6L)).thenReturn("普通用户画像");
        assertThat(tools.context("r",6,Instant.now().plusSeconds(90)).profile()).isEqualTo("普通用户画像");
        verify(profiles).confirmedContext(6L); verify(profiles,never()).confirmedContext(5L);
    }
    @Test void wrongIndexPolicyCannotFallBackToA() {
        assertThatThrownBy(()->tools.context("r",5,Instant.now().plusSeconds(90)).knowledge("Java",Set.of())).hasMessage("B_INDEX_POLICY_MISMATCH");
    }
    @Test void missingProjectDocumentsNeverExpandToWholeKnowledgeBase() {
        activeIndex();
        var result=tools.context("r",5,Instant.now().plusSeconds(90)).projectEvidence("部署",List.of(Map.of("type","BEGINNER_PROJECT","sourceRef","beginner:1","name","acme/demo")));
        assertThat(result.hits()).isEmpty(); assertThat(result.excludedReasons()).containsKey("PROJECT_DOCUMENTS_MISSING");
        verifyNoInteractions(knowledge);
    }
    @Test void projectScopeIsPassedToRetrievalAndRepeatedEvidenceIsStillUsable() {
        activeIndex();
        when(indexes.selectProjectEvidenceDocuments("BEGINNER_PROJECT","beginner:1","acme/demo")).thenReturn(List.of(11L,12L));
        when(knowledge.retrieve(anyString(),anyInt(),eq(.55),any(com.orangejd.server.ragb.retrieval.RagBSearchConstraints.class)))
            .thenReturn(new RagBSearchService.SearchResult("g","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        var context=tools.context("r",5,Instant.now().plusSeconds(90));
        context.knowledge("Java",Set.of());
        context.projectEvidence("部署",List.of(Map.of("type","BEGINNER_PROJECT","sourceRef","beginner:1","name","acme/demo")));
        verify(knowledge).retrieve(eq("部署"),eq(20),eq(.55),argThat((com.orangejd.server.ragb.retrieval.RagBSearchConstraints c)->c.documentIds().equals(Set.of(11L,12L))));
    }
    @Test void recommendationRecallExplicitlyRequestsDistinctProjectsWithoutChangingTheQuery() {
        activeIndex();
        when(knowledge.retrieve(anyString(),anyInt(),eq(.55),any(com.orangejd.server.ragb.retrieval.RagBSearchConstraints.class)))
            .thenReturn(new RagBSearchService.SearchResult("g","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        tools.context("r",5,Instant.now().plusSeconds(90)).projectRecall("AI application development");
        verify(knowledge).retrieve(eq("AI application development"),eq(20),eq(.55),argThat((com.orangejd.server.ragb.retrieval.RagBSearchConstraints c)->c.diversifyProjects()));
    }
    private void activeIndex() {
        var g=new com.orangejd.server.ragb.domain.RagBIndexGeneration(); g.setGenerationKey("g"); g.setEmbeddingModel("bge-m3");
        g.setEmbeddingDimensions(1024); g.setChunkPolicy("STRUCTURE_M3_512_768_64_V1"); g.setAdapterType("HNSW_HYBRID"); when(indexes.selectActiveGeneration()).thenReturn(g);
    }
}
