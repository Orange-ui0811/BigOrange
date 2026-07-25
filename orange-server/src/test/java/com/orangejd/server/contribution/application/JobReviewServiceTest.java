package com.orangejd.server.contribution.application;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.mapper.JobPostingMapper;
import com.orangejd.server.service.DocumentService;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobReviewServiceTest {
    @Test
    void approvalRewardsUserOnceAndPersistsEvidence() {
        JobPostingMapper mapper = mock(JobPostingMapper.class);
        QuotaServiceImpl quota = mock(QuotaServiceImpl.class);
        DocumentService documents = mock(DocumentService.class);
        PricingService pricing = mock(PricingService.class);
        when(pricing.getReward(PricingService.REWARD_JOB_APPROVED)).thenReturn(70);
        JobReviewService service = new JobReviewService(mapper, quota, documents, pricing);
        JobPosting job = pendingJob();
        when(mapper.selectByIdForUpdate(5L)).thenReturn(job);
        when(mapper.updateReviewDecision(5L, "APPROVED", "safe", "AI", 0.97, null)).thenReturn(1);

        service.approve(5L, "AI", 0.97, null, "safe");

        verify(quota).grantReward(7L, 70, "岗位投稿审核通过");
        verify(mapper).updateReviewDecision(5L, "APPROVED", "safe", "AI", 0.97, null);
    }

    @Test
    void nonPendingJobCannotReceiveAnotherReward() {
        JobPostingMapper mapper = mock(JobPostingMapper.class);
        QuotaServiceImpl quota = mock(QuotaServiceImpl.class);
        JobReviewService service = new JobReviewService(
                mapper, quota, mock(DocumentService.class), mock(PricingService.class));
        JobPosting job = pendingJob();
        job.setAuditStatus("APPROVED");
        when(mapper.selectByIdForUpdate(5L)).thenReturn(job);

        assertThrows(BaseException.class, () -> service.approve(5L, "ADMIN", null, 1L, "ok"));
        verify(quota, never()).grantReward(7L, 50, "岗位投稿审核通过");
    }

    private JobPosting pendingJob() {
        JobPosting job = new JobPosting();
        job.setId(5L);
        job.setAuditStatus("PENDING");
        job.setSubmitterId(7L);
        job.setSubmitterRole("USER");
        job.setPosition("Frontend Engineer");
        job.setDirection("Frontend");
        job.setDisplayContent("Build accessible web interfaces");
        return job;
    }
}
