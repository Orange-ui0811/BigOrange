package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.server.dto.CareerJobAssessmentRequest;
import com.orangejd.server.dto.CareerJobOption;
import com.orangejd.server.dto.CareerProjectView;
import com.orangejd.server.service.CareerProjectService;
import com.orangejd.server.service.CareerRoleTaxonomy;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/career-projects")
public class AppCareerProjectController {
    private final CareerProjectService service;

    public AppCareerProjectController(CareerProjectService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<CareerProjectView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.success(service.publicList(keyword, language, difficulty, roleCode, page, size));
    }

    @GetMapping("/weekly")
    public Result<List<CareerProjectView>> weekly() { return Result.success(service.weeklyPicks()); }

    @GetMapping("/role-categories")
    public Result<List<CareerRoleTaxonomy.RoleCategory>> categories() { return Result.success(service.roleCategories()); }

    @GetMapping("/{id}")
    public Result<CareerProjectView> detail(@PathVariable Long id) { return Result.success(service.detail(id, false)); }

    @GetMapping("/{id}/jobs")
    public Result<List<CareerJobOption>> jobs(@PathVariable Long id, @RequestParam String roleCode) {
        service.detail(id, false);
        return Result.success(service.jobs(roleCode).stream()
                .map(job -> new CareerJobOption(job.getId(), job.getPosition(), job.getDirection())).toList());
    }

    @PostMapping("/{id}/job-assessment")
    public Result<Map<String, Object>> assess(@PathVariable Long id,
                                               @Valid @RequestBody CareerJobAssessmentRequest request) {
        return Result.success(service.assessJob(id, request.roleCode(), request.jobId()));
    }
}
