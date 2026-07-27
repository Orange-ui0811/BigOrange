package com.orangejd.server.foundation.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionRunServiceTest {

    @Test
    void returnsExistingRunForDuplicateBusinessKey() {
        IngestionRunMapper mapper = mock(IngestionRunMapper.class);
        IngestionRun existing = new IngestionRun();
        existing.setId(42L);
        when(mapper.selectActiveLock("LEADERBOARD_FULL")).thenReturn(null);
        when(mapper.insertIdempotent(any())).thenReturn(0);
        when(mapper.selectByBusinessKey("LEADERBOARD_FULL", "scheduled:20260727"))
                .thenReturn(existing);

        IngestionRunService service = new IngestionRunService(mapper, new ObjectMapper());
        IngestionRunService.StartResult result = service.create(
                "LEADERBOARD_FULL", "SCHEDULED", "scheduled:20260727", "trend", "v1");

        assertEquals(42L, result.runId());
        assertFalse(result.started());
        verify(mapper, never()).acquireLock(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void marksNewRunCancelledWhenAnotherRunWinsThePipelineLock() {
        IngestionRunMapper mapper = mock(IngestionRunMapper.class);
        when(mapper.selectActiveLock("LEADERBOARD_FULL")).thenReturn(null, 99L);
        when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
            IngestionRun run = invocation.getArgument(0);
            run.setId(10L);
            return 1;
        });

        IngestionRunService service = new IngestionRunService(mapper, new ObjectMapper());
        IngestionRunService.StartResult result = service.create(
                "LEADERBOARD_FULL", "MANUAL", "manual:a", "trend", "v1");

        assertEquals(99L, result.runId());
        assertFalse(result.started());
        verify(mapper).markSuperseded(eq(10L), contains("99"));
    }

    @Test
    void keepsLockWhileRunningTaskAcknowledgesCancellation() {
        IngestionRunMapper mapper = mock(IngestionRunMapper.class);
        when(mapper.requestCancel(7L)).thenReturn(1);
        IngestionRunService service = new IngestionRunService(mapper, new ObjectMapper());

        service.cancel(7L, false);

        verify(mapper, never()).releaseLock(7L);
    }

    @Test
    void keepsRunRunningWhileSomeItemsHaveFailed() {
        IngestionRunMapper mapper = mock(IngestionRunMapper.class);
        IngestionRunService service = new IngestionRunService(mapper, new ObjectMapper());

        service.progress(8L, 48, 20, 2, 0, "部分项目失败");

        verify(mapper).updateProgress(8L, "RUNNING", 45, 48, 20, 2, 0, "部分项目失败");
    }
}
