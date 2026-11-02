package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.contribution.application.JobAiAuditOrchestrator;
import com.orangejd.server.contribution.application.JobReviewService;
import com.orangejd.server.service.JobPostingAccessService;
import com.orangejd.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@ResponseBody
@RequestMapping("/admin/job")
public class AdminJobController {
    private static final Logger log = LoggerFactory.getLogger(AdminJobController.class);

    private final JobPostingAccessService mapper;
    private final DocumentService documentService;
    private final JobReviewService reviewService;
    private final JobAiAuditOrchestrator auditOrchestrator;
    private final RestClient restClient;
    private final String jobWebhookUrl;

    public AdminJobController(JobPostingAccessService mapper,
                              DocumentService documentService,
                              JobReviewService reviewService,
                              JobAiAuditOrchestrator auditOrchestrator,
                              RestClient.Builder restClientBuilder,
                              @Value("${orange.feishu.job-webhook-url:}") String jobWebhookUrl) {
        this.mapper = mapper;
        this.documentService = documentService;
        this.reviewService = reviewService;
        this.auditOrchestrator = auditOrchestrator;
        this.restClient = restClientBuilder.build();
        this.jobWebhookUrl = jobWebhookUrl == null ? "" : jobWebhookUrl.trim();
    }

    @GetMapping("/ai-auto/status")
    public Result<Map<String, Object>> aiStatus() {
        return Result.success(Map.of(
                "enabled", auditOrchestrator.isEnabled(),
                "configured", auditOrchestrator.isConfigured()
        ));
    }

    @PutMapping("/ai-auto/toggle")
    public Result<?> aiToggle(@RequestBody Map<String, Boolean> body) {
        try {
            boolean enabled = body.getOrDefault("enabled", false);
            auditOrchestrator.setEnabled(enabled);
            return Result.success(Map.of("enabled", enabled));
        } catch (IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/import")
    @Transactional
    public Result<Map<String, Object>> importJob(@RequestBody Map<String, Object> body) {
        String company = stringValue(body.get("company"));
        String position = stringValue(body.get("position"));
        String direction = stringValue(body.get("direction"));
        String content = stringValue(body.get("content"));
        String displayContent = stringValue(body.get("displayContent"));
        if (company.isBlank() || position.isBlank() || content.isBlank()) {
            return Result.error("公司、岗位和内容不能为空");
        }
        if (mapper.countByCompanyPosition(company, position) > 0) {
            return Result.error("该岗位已经存在");
        }

        JobPosting job = new JobPosting();
        job.setCompany(company);
        job.setPosition(position);
        job.setDirection(direction);
        job.setContent(content);
        job.setDisplayContent(displayContent.isBlank() ? content : displayContent);
        job.setAuditStatus("APPROVED");
        job.setSubmitterId(BaseContext.getCurrentId());
        job.setSubmitterIp("127.0.0.1");
        job.setSubmitterRole("ADMIN_IMPORT");
        mapper.insert(job);
        try {
            documentService.importJobPosting(job.getId(), position, direction, job.getDisplayContent());
        } catch (Exception ex) {
            log.warn("Imported job knowledge sync failed, jobId={}: {}", job.getId(), ex.getMessage());
        }
        return Result.success(Map.of("id", job.getId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        JobPosting job = mapper.selectById(id);
        if (job == null) return Result.error("岗位不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId());
        result.put("company", job.getCompany());
        result.put("position", job.getPosition());
        result.put("direction", job.getDirection());
        result.put("content", job.getContent());
        result.put("displayContent", job.getDisplayContent());
        result.put("auditStatus", job.getAuditStatus());
        result.put("auditSource", job.getAuditSource());
        result.put("auditConfidence", job.getAuditConfidence());
        result.put("auditReason", job.getAuditReason());
        result.put("submitterId", job.getSubmitterId());
        result.put("createTime", job.getCreateTime());
        return Result.success(result);
    }

    @PostMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        JobPosting job = reviewService.approve(
                id, "ADMIN", null, BaseContext.getCurrentId(), "管理员审核通过");
        notifyApproval(job);
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        reviewService.reject(id, "ADMIN", null, BaseContext.getCurrentId(), body.get("reason"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.success();
    }

    @PostMapping("/{id}/sync-knowledge")
    public Result<?> syncKnowledge(@PathVariable Long id) {
        JobPosting job = mapper.selectById(id);
        if (job == null) return Result.error("岗位不存在");
        try {
            documentService.importJobPosting(job.getId(), job.getPosition(), job.getDirection(), job.getDisplayContent());
            return Result.success();
        } catch (Exception ex) {
            return Result.error("同步失败：" + ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        JobPosting job = mapper.selectById(id);
        if (job == null) return Result.error("岗位不存在");
        mapper.updateContent(
                id,
                body.getOrDefault("position", job.getPosition()),
                body.getOrDefault("direction", job.getDirection()),
                body.getOrDefault("displayContent", job.getDisplayContent())
        );
        return Result.success();
    }

    @GetMapping("/pending")
    public Result<List<JobPosting>> pending(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        int safeSize = PageUtil.capSize(size);
        return Result.success(mapper.selectPending(PageUtil.offset(page, safeSize), safeSize));
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "") String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        int safeSize = PageUtil.capSize(size);
        List<JobPosting> records = mapper.selectAll(status, PageUtil.offset(page, safeSize), safeSize);
        return Result.success(Map.of("records", records, "total", mapper.countAll(status)));
    }

    private void notifyApproval(JobPosting job) {
        if (jobWebhookUrl.isBlank()) return;
        try {
            Map<String, Object> payload = Map.of(
                    "msg_type", "text",
                    "content", Map.of("text", "岗位审核通过：" + job.getPosition() + " / " + job.getDirection())
            );
            restClient.post()
                    .uri(jobWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Job approval notification failed: {}", ex.getMessage());
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
