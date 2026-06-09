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
