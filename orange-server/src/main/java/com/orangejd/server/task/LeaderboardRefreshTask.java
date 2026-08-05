package com.orangejd.server.task;
import com.orangejd.server.service.leaderboard.LeaderboardRefreshCoordinator;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService; import com.orangejd.server.foundation.task.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.context.event.ApplicationReadyEvent; import org.springframework.context.event.EventListener; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
import java.time.*; import java.time.temporal.*; import java.util.Map;
@Component
public class LeaderboardRefreshTask implements SystemTaskHandler {
 public static final String TASK_TYPE="LEADERBOARD_REFRESH"; private final SystemTaskService tasks; private final IngestionCoordinatorService ingestion; private final LeaderboardRefreshCoordinator coordinator; private final boolean pipelineV2Enabled;
 public LeaderboardRefreshTask(SystemTaskService tasks,IngestionCoordinatorService ingestion,LeaderboardRefreshCoordinator coordinator,@Value("${orange.ingestion.pipeline-v2-enabled:false}") boolean enabled){this.tasks=tasks;this.ingestion=ingestion;this.coordinator=coordinator;this.pipelineV2Enabled=enabled;}
 @Scheduled(cron="${orange.leaderboard.refresh-cron:0 0 2 * * 1}",zone="${orange.leaderboard.refresh-zone:Asia/Shanghai}")
 public void scheduleRefresh(){if(pipelineV2Enabled)ingestion.scheduled(IngestionCoordinatorService.LEADERBOARD_FULL,LocalDate.now().toString());else enqueue("scheduled:"+LocalDate.now());}
 public void refreshAll(){coordinator.refreshAll(null,null);}
 @EventListener(ApplicationReadyEvent.class) public void refreshMissingSnapshotsAfterStartup(){if(!coordinator.snapshotsMissingThisWeek())return;var week=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));if(pipelineV2Enabled)ingestion.start(IngestionCoordinatorService.LEADERBOARD_FULL,"STARTUP","startup:"+week,Map.of());else enqueue("startup:"+week);}
 @Override public String taskType(){return TASK_TYPE;}
 @Override public Object execute(SystemTask task){coordinator.refreshAll(task.getRunId(),task.getConfigVersion());return Map.of("refreshedAt",LocalDate.now().toString(),"runId",task.getRunId()==null?0:task.getRunId());}
 static String resolveChatCompletionsUrl(String endpoint){if(endpoint==null||endpoint.isBlank())throw new IllegalStateException("AI endpoint is not configured");String normalized=endpoint.trim().replaceAll("/+$","");return normalized.endsWith("/chat/completions")?normalized:normalized+"/chat/completions";}
 private void enqueue(String key){tasks.enqueue(TASK_TYPE,key,Map.of(),0);}
}
