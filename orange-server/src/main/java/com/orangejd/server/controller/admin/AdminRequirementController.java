package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.RequirementQueryDTO;
import com.orangejd.pojo.vo.RequirementVO;
import com.orangejd.server.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/requirement")
@lombok.RequiredArgsConstructor
public class AdminRequirementController {

    private final RequirementService requirementService;

    @GetMapping("/{id}")
    public Result<RequirementVO> detail(@PathVariable Long id) {
        return Result.success(requirementService.getAdminDetail(id));
    }

    @GetMapping
    public Result<PageResult<RequirementVO>> listAll(RequirementQueryDTO query) {
        return Result.success(requirementService.listAll(query));
    }

    @PutMapping("/{id}/offline")
    public Result<?> offline(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "管理员下架") : "管理员下架";
        requirementService.offline(id, reason);
        return Result.success();
    }
}
