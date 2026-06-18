package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.WorkQueryDTO;
import com.orangejd.pojo.dto.RequirementQueryDTO;
import com.orangejd.pojo.entity.AuditRecord;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.pojo.vo.RequirementVO;
import com.orangejd.server.service.AuditService;
import com.orangejd.server.service.WorkService;
import com.orangejd.server.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {

    @Autowired private AuditService auditService;
    @Autowired private WorkService workService;
    @Autowired private RequirementService requirementService;
    @Autowired(required = false) private com.orangejd.server.service.AiAuditService aiAuditService;

    // ---- AI 托管 ----

    @GetMapping("/ai-auto/status")
    public Result<Map<String, Object>> aiStatus() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("enabled", aiAuditService != null && aiAuditService.isEnabled());
        if (aiAuditService != null) data.put("running", aiAuditService.getRunningCount());
        return Result.success(data);
    }

    @PutMapping("/ai-auto/toggle")
    public Result<Map<String, Object>> aiToggle() {
        if (aiAuditService == null) return Result.error("AI审核服务未配置");
        boolean newState = !aiAuditService.isEnabled();
        aiAuditService.setEnabled(newState);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("enabled", newState);
        return Result.success(data);
    }

    @PostMapping("/ai-auto/run")
    public Result<Map<String, Object>> aiRun() {
        if (aiAuditService == null) return Result.error("AI审核服务未配置");
        int count = aiAuditService.auditAllPending();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("triggered", count);
        return Result.success(data);
    }

    /** 待审作品列表 */
    @GetMapping("/works")
    public Result<PageResult<WorkVO>> pendingWorks(WorkQueryDTO query) {
        query.setAuditStatus("PENDING_REVIEW");
        return Result.success(workService.listAll(query));
    }

    /** 通过作品 */
    @PostMapping("/work/{id}/approve")
    public Result<?> approveWork(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        auditService.auditWork(id, true, reason);
        return Result.success();
    }

    /** 拒绝作品 */
    @PostMapping("/work/{id}/reject")
    public Result<?> rejectWork(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        auditService.auditWork(id, false, reason);
        return Result.success();
    }

    /** 待审需求列表 */
    @GetMapping("/requirements")
    public Result<PageResult<RequirementVO>> pendingRequirements(RequirementQueryDTO query) {
        query.setAuditStatus("PENDING_REVIEW");
        return Result.success(requirementService.listAll(query));
    }

    /** 通过需求 */
    @PostMapping("/requirement/{id}/approve")
    public Result<?> approveRequirement(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        auditService.auditRequirement(id, true, reason);
        return Result.success();
    }

    /** 拒绝需求 */
    @PostMapping("/requirement/{id}/reject")
    public Result<?> rejectRequirement(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        auditService.auditRequirement(id, false, reason);
        return Result.success();
    }

    /** 审核记录 */
    @GetMapping("/history/{targetType}/{targetId}")
    public Result<List<AuditRecord>> auditHistory(@PathVariable String targetType, @PathVariable Long targetId) {
        return Result.success(auditService.getAuditHistory(targetType, targetId));
    }
}
