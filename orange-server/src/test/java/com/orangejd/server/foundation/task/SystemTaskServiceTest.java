package com.orangejd.server.foundation.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemTaskServiceTest {
    @Test
    void claimedTaskIsExecutedAndCompleted() throws Exception {
        SystemTaskMapper mapper = mock(SystemTaskMapper.class);
        SystemTask task = task("TEST", "RUNNING", 1);
        SystemTask candidate = task("TEST", "PENDING", 0);
        when(mapper.selectReady(20)).thenReturn(List.of(candidate));
        when(mapper.claim(9L, "ignored")).thenReturn(0);

        SystemTaskHandler handler = new SystemTaskHandler() {
            public String taskType() { return "TEST"; }
            public Object execute(SystemTask ignored) { return Map.of("ok", true); }
        };
        Executor direct = Runnable::run;
        @SuppressWarnings("unchecked")
        ObjectProvider<SystemTaskHandler> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(java.util.stream.Stream.of(handler));
        SystemTaskService service = new SystemTaskService(mapper, new ObjectMapper(), direct, provider);

        // Worker ids are intentionally opaque, so claim behavior is answered for any worker.
        when(mapper.claim(eq(9L), any())).thenReturn(1);
        when(mapper.selectById(9L)).thenReturn(task);
        service.dispatch();

        verify(mapper).complete(eq(9L), eq("{\"ok\":true}"));
    }

    private SystemTask task(String type, String status, int attempts) {
        SystemTask task = new SystemTask();
        task.setId(9L);
        task.setTaskType(type);
        task.setBusinessKey("key");
        task.setStatus(status);
        task.setPayloadJson("{}");
        task.setAttempts(attempts);
        task.setMaxAttempts(3);
        return task;
    }
}
