package com.orangejd.server.contribution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.contribution.infrastructure.JobAiAuditClient;
import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.mapper.JobPostingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JobAiAuditTaskHandler implements SystemTaskHandler {
    private final ObjectMapper objectMapper;
    private final JobPostingMapper mapper;
    private final JobAiAuditClient client;
    private final JobReviewService reviewService;
    private final double approvalThreshold;

    public JobAiAuditTaskHandler(ObjectMapper objectMapper,
                                 JobPostingMapper mapper,
                                 JobAiAuditClient client,
                                 JobReviewService reviewService,
                                 @Value("${orange.ai-audit.job-approval-threshold:0.90}") double approvalThreshold) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.client = client;
        this.reviewService = reviewService;
        this.approvalThreshold = approvalThreshold;
    }

    @Override
    public String taskType() {
        return JobAiAuditOrchestrator.TASK_TYPE;
    }

    @Override
    public Object execute(SystemTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        long jobId = payload.path("jobId").asLong();
        JobPosting job = mapper.selectById(jobId);
        if (job == null || !"PENDING".equals(job.getAuditStatus())) {
            return Map.of("outcome", "SKIPPED", "reason", "Job is no longer pending");
        }

        JobAiAuditClient.Decision decision = client.review(job);
        if ("APPROVE".equals(decision.action()) && decision.confidence() >= approvalThreshold) {
            reviewService.approve(jobId, "AI", decision.confidence(), null, decision.reason());
            return Map.of("outcome", "APPROVED", "confidence", decision.confidence());
        }
        if ("REJECT".equals(decision.action()) && decision.confidence() >= approvalThreshold) {
            reviewService.reject(jobId, "AI", decision.confidence(), null, decision.reason());
            return Map.of("outcome", "REJECTED", "confidence", decision.confidence());
        }

        reviewService.recordManualReview(jobId, decision.confidence(), decision.reason());
        return Map.of("outcome", "MANUAL_REVIEW", "confidence", decision.confidence());
    }
}
