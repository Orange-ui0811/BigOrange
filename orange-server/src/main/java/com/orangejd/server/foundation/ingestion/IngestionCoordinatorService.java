package com.orangejd.server.foundation.ingestion;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.task.LeaderboardRefreshTask;
import com.orangejd.server.task.OpenSourceRefreshTask;
import com.orangejd.server.task.ReadmePipelineTask;
import com.orangejd.server.task.BeginnerProjectTask;
import com.orangejd.server.task.CareerProjectTask;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IngestionCoordinatorService {
    public static final String LEADERBOARD_FULL = "LEADERBOARD_FULL";
    public static final String ISSUE_FULL = "ISSUE_FULL";
    public static final String ISSUE_VERIFY = "ISSUE_VERIFY";
    public static final String ISSUE_TRANSLATE = "ISSUE_TRANSLATE";
    public static final String README_RETRY = "README_RETRY";
    public static final String BEGINNER_DISCOVERY = "BEGINNER_DISCOVERY";
    public static final String BEGINNER_VERIFY = "BEGINNER_VERIFY";
    public static final String BEGINNER_ENRICH = "BEGINNER_ENRICH";
    public static final String BEGINNER_WEEKLY_PICK = "BEGINNER_WEEKLY_PICK";
    public static final String CAREER_DISCOVERY = "CAREER_DISCOVERY";
    public static final String CAREER_VERIFY = "CAREER_VERIFY";
    public static final String CAREER_ENRICH = "CAREER_ENRICH";
    public static final String CAREER_WEEKLY_PICK = "CAREER_WEEKLY_PICK";
    private static final Set<String> TYPES = Set.of(
            LEADERBOARD_FULL, ISSUE_FULL, ISSUE_VERIFY, ISSUE_TRANSLATE, README_RETRY,
            BEGINNER_DISCOVERY, BEGINNER_VERIFY, BEGINNER_ENRICH, BEGINNER_WEEKLY_PICK,
            CAREER_DISCOVERY, CAREER_VERIFY, CAREER_ENRICH, CAREER_WEEKLY_PICK);

    private final IngestionRunService runService;
    private final SystemTaskService taskService;
    private final AiRuntimeConfigService configService;

    public IngestionCoordinatorService(IngestionRunService runService,
                                       SystemTaskService taskService,
                                       AiRuntimeConfigService configService) {
        this.runService = runService;
        this.taskService = taskService;
        this.configService = configService;
    }

    public IngestionRunService.StartResult start(String pipelineType, String triggerSource,
                                                  String businessKey, Map<String, Object> options) {
        if (!TYPES.contains(pipelineType)) throw new IllegalArgumentException("不支持的抓取任务类型");
        AiRuntimeConfig config = configService.current(AiRuntimeConfigService.TEXT);
        String key = businessKey == null || businessKey.isBlank()
                ? triggerSource.toLowerCase() + ":" + UUID.randomUUID()
                : businessKey;
        IngestionRunService.StartResult result = runService.create(
                pipelineType, triggerSource, key, AiRuntimeConfigService.TEXT, config.version());
        if (result.started()) enqueue(result.runId(), pipelineType, config.version(), options);
        return result;
    }

    public IngestionRunService.StartResult scheduled(String pipelineType, String bucket) {
        return start(pipelineType, "SCHEDULED", "scheduled:" + bucket, Map.of());
    }

    public IngestionRunService.StartResult retry(Long sourceRunId) {
        IngestionRun source = runService.get(sourceRunId);
        if (source == null) throw new IllegalArgumentException("原运行记录不存在");
        return start(source.getPipelineType(), "RETRY",
                "retry:" + sourceRunId + ":" + UUID.randomUUID(), Map.of("retryOf", sourceRunId));
    }

    public boolean cancel(Long runId) {
        taskService.cancelRun(runId);
        return runService.cancel(runId, !taskService.hasRunningTask(runId));
    }

    private void enqueue(Long runId, String pipelineType, String configVersion,
                         Map<String, Object> options) {
        String mode;
        String taskType;
        int maxAttempts;
        if (pipelineType.startsWith("CAREER_")) {
            mode = CAREER_DISCOVERY.equals(pipelineType) ? "DISCOVERY"
                    : CAREER_VERIFY.equals(pipelineType) ? "VERIFY"
                    : CAREER_ENRICH.equals(pipelineType) ? "ENRICH" : "WEEKLY_PICK";
            taskType = CareerProjectTask.TASK_TYPE;
            maxAttempts = 3;
        } else if (pipelineType.startsWith("BEGINNER_")) {
            mode = BEGINNER_DISCOVERY.equals(pipelineType) ? "DISCOVERY"
                    : BEGINNER_VERIFY.equals(pipelineType) ? "VERIFY"
                    : BEGINNER_ENRICH.equals(pipelineType) ? "ENRICH" : "WEEKLY_PICK";
            taskType = pipelineType;
            maxAttempts = 3;
        } else if (LEADERBOARD_FULL.equals(pipelineType)) {
            mode = "FULL";
            taskType = LeaderboardRefreshTask.TASK_TYPE;
            maxAttempts = 0;
        } else if (README_RETRY.equals(pipelineType)) {
            mode = "RETRY";
            taskType = ReadmePipelineTask.TASK_TYPE;
            maxAttempts = 5;
        } else {
            mode = ISSUE_FULL.equals(pipelineType) ? "FULL"
                    : ISSUE_VERIFY.equals(pipelineType) ? "VERIFY" : "TRANSLATE";
            taskType = OpenSourceRefreshTask.TASK_TYPE;
            maxAttempts = 8;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>(options);
        payload.put("mode", mode);
        taskService.enqueue(runId, taskType, "run:" + runId, payload, maxAttempts, configVersion);
    }

    public static String currentBucket(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }
}
