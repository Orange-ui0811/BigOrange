package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Platform terminal protection must hold for either orchestration implementation. */
class CustomerEngineLifecycleTest {
    final CustomerStore store=mock(CustomerStore.class);
    final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerConversation conversation=mock(CustomerConversation.class);
    final CustomerProperties config=new CustomerProperties();
    final AtomicReference<String> status=new AtomicReference<>("QUEUED");
    final CountDownLatch archived=new CountDownLatch(1);
    final CountDownLatch terminated=new CountDownLatch(1);
    CustomerRuns runs(CustomerExecutionEngine.Kind kind,java.util.function.Function<CustomerRunContext,CustomerExecutionResult> execution,Instant deadline) {
        var row=Map.<String,Object>of("id","r","status","QUEUED","execution_engine",kind.name(),"deadline_at",deadline);
        when(store.submit(1,5,"request01","你好")).thenReturn(row);
        when(db.run("r")).thenAnswer(i->Map.of("id","r","status",status.get()));
        when(store.run("r",5)).thenAnswer(i->db.run("r"));
        when(conversation.metrics(anyList())).thenReturn(Map.of("costUsd","UNKNOWN"));
        doAnswer(i->{status.compareAndSet("QUEUED",i.getArgument(1));terminated.countDown();return null;}).when(store).terminal(eq("r"),anyString(),anyString(),anyMap());
        doAnswer(i->{archived.countDown();return 1;}).when(db).metrics(eq("r"),anyString());
        // Deliberately disagree with persisted selection; changing config cannot switch an accepted run.
        config.setExecutionEngine(kind==CustomerExecutionEngine.Kind.LEGACY?CustomerExecutionEngine.Kind.LANGGRAPH4J:CustomerExecutionEngine.Kind.LEGACY);
        var engine=new CustomerExecutionEngine(){public Kind kind(){return kind;}public CustomerExecutionResult execute(CustomerRunContext context){return execution.apply(context);}};
        return new CustomerRuns(store,db,conversation,config,new CustomerJson(new ObjectMapper()),List.of(engine),null);
    }
    CustomerExecutionResult output(){return new CustomerExecutionResult(Map.of("answer","完成"),Map.of());}
    @ParameterizedTest @EnumSource(CustomerExecutionEngine.Kind.class)
    void cancelledRunIgnoresAnUninterruptibleLateResult(CustomerExecutionEngine.Kind kind) throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var harness=new Harness(runs(kind,c->{started.countDown();boolean released=false;while(!released)try{released=release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}return output();},Instant.now().plusSeconds(20)))) {
            harness.runs.submit(1,5,"request01","你好");assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();
            harness.runs.cancel("r",5);release.countDown();assertThat(archived.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(status).hasValue("CANCELLED");verify(store,never()).complete(anyString(),anyMap(),anyMap(),any());
        } finally {release.countDown();}
    }
    @ParameterizedTest @EnumSource(CustomerExecutionEngine.Kind.class)
    void expiredAcceptedDeadlineNeverCallsTheEngine(CustomerExecutionEngine.Kind kind) throws Exception {
        var calls=new AtomicInteger();try(var harness=new Harness(runs(kind,c->{calls.incrementAndGet();return output();},Instant.now().minusSeconds(1)))) {
            harness.runs.submit(1,5,"request01","你好");assertThat(terminated.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(0);assertThat(status).hasValue("TIMED_OUT");verify(store,never()).complete(anyString(),anyMap(),anyMap(),any());
        }
    }
    @ParameterizedTest @EnumSource(CustomerExecutionEngine.Kind.class)
    void finalCommitFailureIsNotReportedAsSuccess(CustomerExecutionEngine.Kind kind) throws Exception {
        doThrow(new IllegalStateException("COMMIT_FAILED")).when(store).complete(anyString(),anyMap(),anyMap(),any());
        try(var harness=new Harness(runs(kind,c->output(),Instant.now().plusSeconds(20)))) {
            harness.runs.submit(1,5,"request01","你好");assertThat(archived.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(status).hasValue("FAILED");verify(store).terminal(eq("r"),eq("FAILED"),eq("COMMIT_FAILED"),anyMap());
        }
    }
    @ParameterizedTest @EnumSource(CustomerExecutionEngine.Kind.class)
    void acceptedRunUsesPersistedEngineAndRestartNeverReplays(CustomerExecutionEngine.Kind kind) throws Exception {
        config.setEnabled(true);when(db.unfinished()).thenReturn(List.of("old"));var calls=new AtomicInteger();
        try(var harness=new Harness(runs(kind,c->{calls.incrementAndGet();return output();},Instant.now().plusSeconds(20)))) {
            harness.runs.recover();verify(store).terminal(eq("old"),eq("INTERRUPTED"),eq("SERVER_RESTARTED"),anyMap());
            harness.runs.submit(1,5,"request01","你好");assertThat(archived.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);verify(store).complete(eq("r"),anyMap(),anyMap(),any());
        }
    }
    record Harness(CustomerRuns runs) implements AutoCloseable {public void close(){runs.close();}}
}
