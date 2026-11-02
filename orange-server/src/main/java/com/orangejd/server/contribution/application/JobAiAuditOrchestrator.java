package com.orangejd.server.contribution.application;

import com.orangejd.server.contribution.infrastructure.JobAiAuditClient;
import com.orangejd.server.foundation.task.SystemTaskService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class JobAiAuditOrchestrator {
    public static final String TASK_TYPE = "JOB_AI_AUDIT";
    private static final String ENABLED_KEY = "system:job-ai-audit:enabled";

    private final StringRedisTemplate redisTemplate;
    private final SystemTaskService taskService;
    private final JobAiAuditClient client;

    public JobAiAuditOrchestrator(StringRedisTemplate redisTemplate,
                                  SystemTaskService taskService,
                                  JobAiAuditClient client) {
        this.redisTemplate = redisTemplate;
        this.taskService = taskService;
        this.client = client;
    }

    public boolean isEnabled() {
        return "true".equals(redisTemplate.opsForValue().get(ENABLED_KEY));
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !client.isConfigured()) {
            throw new IllegalStateException("请先配置并测试 AI 审核服务");
        }
        redisTemplate.opsForValue().set(ENABLED_KEY, String.valueOf(enabled));
    }

    public Long enqueueIfEnabled(Long jobId) {
        if (!isEnabled()) return null;
        return taskService.enqueue(TASK_TYPE, "job:" + jobId, Map.of("jobId", jobId), 5);
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }
}
