package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerRunsTest {
    @Test void queuedCancellationDoesNotExecuteAndReplayDoesNotDuplicate() throws Exception {
        var store=mock(CustomerStore.class);var db=mock(CustomerMapper.class);var conversation=mock(CustomerConversation.class);
        var props=new CustomerProperties();props.setMaxConcurrent(1);props.setMaxQueued(1);
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        for(String id:List.of("one","two")) {
            var row=Map.<String,Object>of("id",id,"status","QUEUED");when(store.submit(1,5,id,"hi")).thenReturn(row);when(db.run(id)).thenReturn(row);
        }
        doAnswer(i->{started.countDown();release.await(3,TimeUnit.SECONDS);return null;}).when(conversation).prepare(argThat(c -> c.runId().equals("one")));
        when(conversation.metrics(anyList())).thenReturn(Map.of());
        var runs=new CustomerRuns(store,db,conversation,props,new CustomerJson(new ObjectMapper()));
        try {
            runs.submit(1,5,"one","hi");assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();
            runs.submit(1,5,"two","hi");verify(conversation,never()).prepare(argThat(c -> c.runId().equals("two")));
            when(store.submit(1,5,"two","hi")).thenReturn(Map.of("id","two","status","QUEUED","replayed",true));
            runs.submit(1,5,"two","hi");
            when(store.run("two",5)).thenReturn(Map.of("id","two","status","CANCELLED"));
            runs.cancel("two",5);release.countDown();
            verify(conversation,never()).prepare(argThat(c -> c.runId().equals("two")));
            verify(store).terminal(eq("two"),eq("CANCELLED"),eq("USER_CANCELLED"),anyMap());
        } finally {release.countDown();runs.close();}
    }
}
