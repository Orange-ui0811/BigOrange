package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.RequirementCreateDTO;
import com.orangejd.pojo.dto.RequirementEditDTO;
import com.orangejd.pojo.dto.RequirementQueryDTO;
import com.orangejd.pojo.vo.RequirementVO;
import com.orangejd.server.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/requirement")
public class AppRequirementController {

    @Autowired private RequirementService requirementService;

    @PostMapping
    public Result<RequirementVO> create(@RequestBody RequirementCreateDTO dto) {
        return Result.success(requirementService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<RequirementVO> edit(@PathVariable Long id, @RequestBody RequirementEditDTO dto) {
        return Result.success(requirementService.edit(id, dto));
    }

    @PostMapping("/{id}/submit")
    public Result<?> submitForReview(@PathVariable Long id) {
        requirementService.submitForReview(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        requirementService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/visibility")
    public Result<?> toggleVisibility(@PathVariable Long id) {
        requirementService.toggleVisibility(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<RequirementVO> detail(@PathVariable Long id) {
        return Result.success(requirementService.getDetail(id));
    }

    @GetMapping("/my")
    public Result<PageResult<RequirementVO>> myRequirements(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(requirementService.listMyRequirements(page, size));
    }

    @GetMapping("/public")
    public Result<PageResult<RequirementVO>> publicList(RequirementQueryDTO query) {
        return Result.success(requirementService.listPublic(query));
    }
}
