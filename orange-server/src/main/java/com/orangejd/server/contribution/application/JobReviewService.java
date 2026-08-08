package com.orangejd.server.contribution.application;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.mapper.JobPostingMapper;
import com.orangejd.server.service.DocumentService;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobReviewService {
    private static final Logger log = LoggerFactory.getLogger(JobReviewService.class);
    private final JobPostingMapper mapper;
    private final QuotaServiceImpl quotaService;
    private final DocumentService documentService;
    private final PricingService pricingService;

    public JobReviewService(JobPostingMapper mapper,
                            QuotaServiceImpl quotaService,
                            DocumentService documentService,
                            PricingService pricingService) {
        this.mapper = mapper;
        this.quotaService = quotaService;
        this.documentService = documentService;
        this.pricingService = pricingService;
    }

    @Transactional
    public JobPosting approve(Long id, String source, Double confidence, Long reviewerId, String reason) {
        JobPosting job = requirePending(id);
        if (job.getSubmitterId() != null && "USER".equals(job.getSubmitterRole())) {
            int reward = pricingService.getReward(PricingService.REWARD_JOB_APPROVED);
            if (reward > 0) {
                quotaService.grantReward(job.getSubmitterId(), PricingService.REWARD_JOB_APPROVED,
                        "job:" + id, "岗位投稿审核通过");
            }
        }
        if (mapper.updateReviewDecision(id, "APPROVED", reason, source, confidence, reviewerId) != 1) {
            throw new BaseException("岗位状态已变化，请刷新后重试");
        }
        try {
            documentService.importJobPosting(job.getId(), job.getPosition(), job.getDirection(), job.getDisplayContent());
        } catch (Exception ex) {
            log.warn("Job knowledge synchronization deferred, jobId={}: {}", id, ex.getMessage());
        }
        return job;
    }

    @Transactional
    public JobPosting reject(Long id, String source, Double confidence, Long reviewerId, String reason) {
        JobPosting job = requirePending(id);
        String safeReason = reason == null || reason.isBlank() ? "未通过审核" : reason.trim();
        if (mapper.updateReviewDecision(id, "REJECTED", safeReason, source, confidence, reviewerId) != 1) {
            throw new BaseException("岗位状态已变化，请刷新后重试");
        }
        return job;
    }

    @Transactional
    public void recordManualReview(Long id, Double confidence, String reason) {
        JobPosting job = mapper.selectByIdForUpdate(id);
        if (job == null || !"PENDING".equals(job.getAuditStatus())) return;
        mapper.updateReviewDecision(id, "PENDING", reason, "AI", confidence, null);
    }

    private JobPosting requirePending(Long id) {
        JobPosting job = mapper.selectByIdForUpdate(id);
        if (job == null) throw new BaseException("岗位不存在");
        if (!"PENDING".equals(job.getAuditStatus())) {
            throw new BaseException("岗位已经处理，请勿重复审核");
        }
        return job;
    }
}
