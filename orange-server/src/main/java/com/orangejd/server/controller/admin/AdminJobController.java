package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.mapper.JobPostingMapper;
import com.orangejd.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/job")
public class AdminJobController {

    private static final Logger log = LoggerFactory.getLogger(AdminJobController.class);

    @Autowired private JobPostingMapper mapper;
    @Autowired private DocumentService documentService;
    @Autowired private com.orangejd.server.service.impl.QuotaServiceImpl quotaService;
    @Autowired(required = false) private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${orange.feishu.job-webhook-url:}")
    private String jobWebhookUrl;

    private static final String AI_AUDIT_KEY = "system:job-ai-audit:enabled";

    @GetMapping("/ai-auto/status")
    public Result<Map<String, Object>> aiStatus() {
        boolean enabled = redisTemplate != null && "true".equals(redisTemplate.opsForValue().get(AI_AUDIT_KEY));
        return Result.success(Map.of("enabled", enabled));
    }

    @PutMapping("/ai-auto/toggle")
    public Result<?> aiToggle(@RequestBody Map<String, Boolean> body) {
        if (redisTemplate == null) return Result.error("Redis不可用");
        boolean enabled = body.getOrDefault("enabled", false);
        redisTemplate.opsForValue().set(AI_AUDIT_KEY, String.valueOf(enabled));
        if (enabled) {
            new Thread(() -> {
                List<JobPosting> pending = mapper.selectPending(0, 100);
                for (JobPosting jp : pending) {
                    try { approve(jp.getId()); log.info("AI自动审核通过岗位: {}", jp.getPosition()); }
                    catch (Exception e) { log.warn("AI审核失败: {}", e.getMessage()); }
                }
            }).start();
        }
        return Result.success(Map.of("enabled", enabled));
    }

    /** 批量导入已有 MD 文件 */
    @PostMapping("/import")
    @Transactional
    public Result<Map<String, Object>> importJob(@RequestBody Map<String, Object> body) {
        String company = (String) body.get("company");
        String position = (String) body.get("position");
        String direction = (String) body.get("direction");
        String content = (String) body.get("content");
        String displayContent = (String) body.get("displayContent");

        if (company == null || position == null || content == null) {
            return Result.error("公司、岗位、内容不能为空");
        }

        // 查重
        if (mapper.countByCompanyPosition(company, position) > 0) {
            return Result.error("已存在: " + company + " - " + position);
        }

        // 入库
        JobPosting jp = new JobPosting();
        jp.setCompany(company);
        jp.setPosition(position);
        jp.setDirection(direction != null ? direction : "");
        jp.setContent(content);
        jp.setDisplayContent(displayContent != null ? displayContent : content);
        jp.setAuditStatus("APPROVED");
        jp.setSubmitterId(1L);
        jp.setSubmitterIp("127.0.0.1");
        jp.setSubmitterRole("ADMIN_IMPORT");
        mapper.insert(jp);

        // 向量化入库
        if (displayContent != null && !displayContent.isEmpty()) {
            try {
                documentService.importJobPosting(jp.getId(), position, direction, displayContent);
            } catch (Exception e) {
                log.warn("向量化失败: {}", e.getMessage());
            }
        }

        return Result.success(Map.of("id", jp.getId()));
    }

    /** 查看详情（含公司名，管理员可见） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        JobPosting jp = mapper.selectById(id);
        if (jp == null) return Result.error("不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", jp.getId());
        m.put("company", jp.getCompany());
        m.put("position", jp.getPosition());
        m.put("direction", jp.getDirection());
        m.put("content", jp.getContent());
        m.put("displayContent", jp.getDisplayContent());
        m.put("auditStatus", jp.getAuditStatus());
        m.put("submitterId", jp.getSubmitterId());
        m.put("createTime", jp.getCreateTime() != null ? jp.getCreateTime().toString() : "");
        return Result.success(m);
    }

    /** 审核通过 */
    @PostMapping("/{id}/approve")
    @Transactional
    public Result<?> approve(@PathVariable Long id) {
        JobPosting jp = mapper.selectByIdForUpdate(id);
        if (jp == null) return Result.error("不存在");
        if (!"PENDING".equals(jp.getAuditStatus())) return Result.error("状态不正确");
        mapper.updateAuditStatus(id, "APPROVED", null);

        try {
            documentService.importJobPosting(jp.getId(), jp.getPosition(), jp.getDirection(), jp.getDisplayContent());
        } catch (Exception e) {
            log.warn("向量化失败: {}", e.getMessage());
        }
        // 发奖励
        try { quotaService.grantReward(jp.getSubmitterId(), 50, "岗位投稿审核通过"); } catch (Exception ignored) {}
        // 飞书通知（岗位审核专用 webhook）
        if (jobWebhookUrl != null && !jobWebhookUrl.isEmpty()) {
            try {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("msg_type", "interactive");
                Map<String, Object> cardBody = new LinkedHashMap<>();
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("template", "green");
                Map<String, Object> titleObj = new LinkedHashMap<>();
                titleObj.put("tag", "plain_text");
                titleObj.put("content", "✅ 岗位审核通过");
                header.put("title", titleObj);
                cardBody.put("header", header);
                Map<String, Object> element = new LinkedHashMap<>();
                element.put("tag", "markdown");
                element.put("content", "**岗位：**" + jp.getPosition() + "\n**方向：**" + jp.getDirection());
                cardBody.put("elements", java.util.Collections.singletonList(element));
                card.put("card", cardBody);
                new org.springframework.web.client.RestTemplate().postForEntity(jobWebhookUrl, card, String.class);
            } catch (Exception ignored) {}
        }
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.success();
    }

    /** 同步到知识库 */
    @PostMapping("/{id}/sync-knowledge")
    public Result<?> syncKnowledge(@PathVariable Long id) {
        JobPosting jp = mapper.selectById(id);
        if (jp == null) return Result.error("不存在");
        try {
            documentService.importJobPosting(jp.getId(), jp.getPosition(), jp.getDirection(), jp.getDisplayContent());
        } catch (Exception e) { return Result.error("同步失败: " + e.getMessage()); }
        return Result.success();
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        JobPosting jp = mapper.selectById(id);
        if (jp == null) return Result.error("不存在");
        String position = body.getOrDefault("position", jp.getPosition());
        String direction = body.getOrDefault("direction", jp.getDirection());
        String displayContent = body.getOrDefault("displayContent", jp.getDisplayContent());
        mapper.updateContent(id, position, direction, displayContent);
        return Result.success();
    }

    /** 拒绝 */
    @PostMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        mapper.updateAuditStatus(id, "REJECTED", reason);
        return Result.success();
    }

    /** 待审列表 */
    @GetMapping("/pending")
    public Result<List<JobPosting>> pending(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        return Result.success(mapper.selectPending(offset, Math.min(size, 100)));
    }

    /** 全部列表 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "") String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        int offset = (page - 1) * size;
        List<JobPosting> list = mapper.selectAll(status, offset, Math.min(size, 100));
        long total = mapper.countAll(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", total);
        return Result.success(result);
    }
}
