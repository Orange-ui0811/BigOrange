package com.orangejd.server.task;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshTaskTest {

    @Test
    void appendsChatCompletionsPathToBaseEndpoint() {
        assertEquals(
                "https://ai.gitee.com/v1/chat/completions",
                LeaderboardRefreshTask.resolveChatCompletionsUrl("https://ai.gitee.com/v1/"));
    }

    @Test
    void keepsCompleteChatCompletionsEndpointUnchanged() {
        assertEquals(
                "https://ai.gitee.com/v1/chat/completions",
                LeaderboardRefreshTask.resolveChatCompletionsUrl(
                        "https://ai.gitee.com/v1/chat/completions"));
    }

    @Test
    void rejectsMissingEndpoint() {
        assertThrows(
                IllegalStateException.class,
                () -> LeaderboardRefreshTask.resolveChatCompletionsUrl(" "));
    }

    @Test
    void taskFacadeKeepsSchedulingAndExecutionContracts() {
        SystemTaskService tasks = mock(SystemTaskService.class);
        IngestionCoordinatorService ingestion = mock(IngestionCoordinatorService.class);
        LeaderboardRefreshCoordinator coordinator = mock(LeaderboardRefreshCoordinator.class);
        LeaderboardRefreshTask legacyTask = new LeaderboardRefreshTask(tasks, ingestion, coordinator, false);

        legacyTask.scheduleRefresh();
        SystemTask task = new SystemTask();
        task.setRunId(17L);
        task.setConfigVersion("v1");
        Map<?, ?> result = (Map<?, ?>) legacyTask.execute(task);

        verify(tasks).enqueue(eq(LeaderboardRefreshTask.TASK_TYPE), any(), eq(Map.of()), eq(0));
        verify(coordinator).refreshAll(17L, "v1");
        assertEquals(17L, result.get("runId"));
    }

    @Test
    void startupCheckUsesAggregateSnapshotsAndLegacyNullFallback() {
        LeaderboardSnapshotMapper snapshots = mock(LeaderboardSnapshotMapper.class);
        LeaderboardRefreshCoordinator coordinator = new LeaderboardRefreshCoordinator(snapshots,
                mock(LeaderboardSnapshotRefresher.class), mock(LeaderboardTranslationPipeline.class),
                mock(LeaderboardReadmeIndexer.class), mock(LeaderboardProgressReporter.class));

        assertTrue(coordinator.snapshotsMissingThisWeek());

        LeaderboardSnapshot fresh = new LeaderboardSnapshot();
        fresh.setSnapshotDate(LocalDate.now());
        when(snapshots.selectLatest(eq("github"), eq(LeaderboardRefreshCoordinator.ALL_TOPICS), any()))
                .thenReturn(fresh);
        assertFalse(coordinator.snapshotsMissingThisWeek());
    }
}
