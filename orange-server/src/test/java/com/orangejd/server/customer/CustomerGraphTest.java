package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerGraphTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerStore store=mock(CustomerStore.class);final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerModel model=mock(CustomerModel.class);final CustomerTools tools=mock(CustomerTools.class);
    final CustomerTools.Context tool=mock(CustomerTools.Context.class);final CustomerProjects projects=mock(CustomerProjects.class);
    final CustomerGraphRepository repository=mock(CustomerGraphRepository.class);
    final CustomerExecutionTrace trace=new CustomerExecutionTrace(repository,json,new SimpleMeterRegistry());
    final CustomerProperties config=new CustomerProperties();
    final Map<String,List<Map<String,Object>>> snapshots=new HashMap<>();
    CustomerConversation conversation;
    @BeforeEach void setup(){
        when(db.run(anyString())).thenReturn(Map.of("account_id",5,"session_id",1,"message","请介绍一下项目"));
        when(store.session(1,5)).thenReturn(CustomerWorkflowFixtures.session("{}"));
        when(tools.context(anyString(),eq(5L),any())).thenReturn(tool);when(tool.profile()).thenReturn("");
        org.springframework.test.util.ReflectionTestUtils.setField(tool,"run","r");
        when(repository.checkpoints(anyString())).thenAnswer(i->snapshots.getOrDefault(i.getArgument(0),List.of()));
        doAnswer(i->{String run=i.getArgument(0);var row=new LinkedHashMap<String,Object>();row.put("checkpoint_id",i.getArgument(1));row.put("node_id",i.getArgument(2));row.put("next_node_id",i.getArgument(3));row.put("state_json",i.getArgument(4));snapshots.computeIfAbsent(run,k->new ArrayList<>()).addFirst(row);return null;}).when(repository).checkpoint(anyString(),anyString(),anyString(),nullable(String.class),anyString());
        conversation=new CustomerConversation(store,db,json,model,tools,projects,config);
    }
    CustomerExecutionResult graph(){return new LangGraphCustomerEngine(conversation,repository,json,trace).execute(new CustomerRunContext("r",Instant.now().plusSeconds(90),new ArrayList<>()));}
    @ParameterizedTest @ValueSource(strings={"STATE","CHAT","EXPLAIN","DIAGNOSE","COLLECT","RECOMMEND","DETAIL","COMPARE","QUESTION"})
    void everyIntentRunsInGraphWithoutCommitting(String intent){
        when(model.call(anyString(),any(),any(),anyList())).thenReturn(Map.of("intent",intent,"reply","你好，一起找适合你的项目。","changes",Map.of()));
        when(tool.knowledge(anyString(),anySet())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("g","HNSW_HYBRID",0,0,Map.of(),List.of(),List.of(),"REFUSE_NO_MATCH"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var captured=org.mockito.ArgumentCaptor.forClass(Map.class);var confirmed=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),captured.capture(),confirmed.capture(),any());
        var expected=new CustomerExecutionResult(captured.getValue(),confirmed.getValue());
        var expectedModelCalls=callShape(model);var expectedTools=callShape(tool);
        clearInvocations(store,model,tool);
        var actual=graph();
        assertThat(actual.result()).extracting("status","intent","answer","projects","references").isEqualTo(List.of(expected.result().get("status"),expected.result().get("intent"),expected.result().get("answer"),expected.result().get("projects"),expected.result().get("references")));
        assertThat(actual.result()).containsEntry("workflowVersion",2);
        assertThat(callShape(model)).isEqualTo(expectedModelCalls);
        assertThat(callShape(tool)).isEqualTo(expectedTools);
        verify(store,never()).complete(anyString(),anyMap(),anyMap(),any());
        assertThat(snapshots.get("r")).anyMatch(c->"validate_response".equals(c.get("node_id")));
    }
    List<String> callShape(Object mock) {
        return mockingDetails(mock).getInvocations().stream()
            .filter(i->!Set.of("check","observe").contains(i.getMethod().getName()))
            .map(i->i.getMethod().getName()+":"+json.write(Arrays.stream(i.getArguments()).filter(a->!(a instanceof Instant)).filter(a->!(a instanceof List<?> list) || !list.isEmpty()).toList())).toList();
    }
    @Test void initialCheckpointFailureDoesNotCallProfileOrModel(){
        doThrow(new IllegalStateException("DB_DOWN")).when(repository).checkpoint(anyString(),anyString(),anyString(),nullable(String.class),anyString());
        assertThatThrownBy(this::graph).hasMessageContaining("CHECKPOINT_WRITE_FAILED");
        verifyNoInteractions(model);verify(tool,never()).profile();
    }
    @Test void middleCheckpointFailureStopsBeforeUnderstanding(){
        doAnswer(i->{if("load_context".equals(i.getArgument(2)))throw new IllegalStateException("DB_DOWN");return null;}).when(repository).checkpoint(anyString(),anyString(),anyString(),nullable(String.class),anyString());
        assertThatThrownBy(this::graph).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(model);verify(store,never()).complete(anyString(),anyMap(),anyMap(),any());
    }
    @Test void cancellationAfterModelReturnCannotAdvance(){
        when(model.call(anyString(),any(),any(),anyList())).thenAnswer(i->{doThrow(new IllegalStateException("RUN_STOPPED")).when(tool).check();return Map.of("intent","CHAT","reply","迟到","changes",Map.of());});
        assertThatThrownBy(this::graph).isInstanceOf(RuntimeException.class);
        verify(store,never()).checkpoint(anyString(),anyMap());
        verify(store,never()).complete(anyString(),anyMap(),anyMap(),any());
    }
    @Test void saverScopesHistoryAndCopiesJsonWithoutJavaDeserialization() throws Exception {
        var saver=new CustomerMysqlCheckpointSaver("r",repository,json);var cfg=RunnableConfig.builder().threadId("customer:r").build();
        var data=new HashMap<String,Object>(Map.of("id",Long.MAX_VALUE,"conditions",new ArrayList<>(List.of("Java"))));
        saver.put(cfg,Checkpoint.builder().id("first").nodeId("load_context").nextNodeId("understand_turn").state(data).build());
        data.put("id",0);assertThat(saver.get(cfg).orElseThrow().getState()).containsEntry("id",Long.MAX_VALUE);
        assertThatThrownBy(()->saver.get(RunnableConfig.builder().threadId("customer:other").build())).hasMessage("CHECKPOINT_RUN_MISMATCH");
        assertThatThrownBy(()->saver.put(cfg,Checkpoint.builder().id("oversize").nodeId("n").nextNodeId("END").state(Map.of("text","中".repeat(100000))).build())).hasMessage("CHECKPOINT_TOO_LARGE");
        saver.release(cfg);verify(repository).archive("r","GRAPH_COMPLETE",null);
        assertThat(saver.list(cfg)).hasSize(1);
    }
    @Test void jsonSerializerPreservesLargeIdsAndNestedValues() throws Exception {
        var serializer=new CustomerGraphSerializer(json);var data=Map.<String,Object>of("id",Long.MAX_VALUE,"nested",Map.of("items",List.of("中文",false,180)));
        assertThat(serializer.dataFromBytes(serializer.dataToBytes(data))).isEqualTo(data);
    }
    @Test void checkpointCacheReadsOnceAndFailedWritesCannotAdvanceIt() {
        var saver=new CustomerMysqlCheckpointSaver("r",repository,json);var cfg=RunnableConfig.builder().threadId("customer:r").build();
        saver.put(cfg,Checkpoint.builder().id("one").nodeId("load_context").nextNodeId("understand_turn").state(Map.of("next","understand_turn")).build());
        assertThat(saver.get(cfg).orElseThrow().getId()).isEqualTo("one");
        saver.get(cfg).orElseThrow().getState().put("next","corrupt");
        assertThat(saver.get(cfg).orElseThrow().getState()).containsEntry("next","understand_turn");
        saver.put(cfg,Checkpoint.builder().id("two").nodeId("understand_turn").nextNodeId("reconcile_conditions").state(Map.of("next","reconcile_conditions")).build());
        assertThat(saver.get(cfg).orElseThrow().getId()).isEqualTo("two");
        assertThat(saver.get(RunnableConfig.builder(cfg).checkPointId("one").build()).orElseThrow().getId()).isEqualTo("one");
        doThrow(new IllegalStateException("DB_DOWN")).when(repository).checkpoint(eq("r"),eq("three"),anyString(),nullable(String.class),anyString());
        assertThatThrownBy(()->saver.put(cfg,Checkpoint.builder().id("three").nodeId("n").nextNodeId("END").state(Map.of()).build())).hasMessage("CHECKPOINT_WRITE_FAILED");
        assertThat(saver.list(cfg)).hasSize(2);verify(repository,times(1)).checkpoints("r");
    }
    @Test void candidateSelectionFailureKeepsFrozenRecoveryBoundary(){
        var turn=conversation.newTurn(new CustomerRunContext("r",Instant.now().plusSeconds(90),new ArrayList<>()));
        turn.state=new LinkedHashMap<>(Map.of("topic",List.of("RAG")));turn.result=new LinkedHashMap<>();turn.context=tool;turn.policy="test";
        when(projects.select(anyList(),anyMap(),anyList())).thenThrow(new IllegalStateException("SELECTION_UNAVAILABLE"));
        turn.recommendationSteps.select(turn);
        assertThat(turn.result).containsEntry("status","RELEVANCE_UNAVAILABLE");
        assertThat(turn.state).containsEntry("lastCandidates",List.of());
        verify(tool,never()).diagnose(anyMap());
    }
}
