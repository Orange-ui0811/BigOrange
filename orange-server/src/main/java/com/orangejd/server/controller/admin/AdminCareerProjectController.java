package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.server.dto.BeginnerProjectStatusRequest;
import com.orangejd.server.dto.BeginnerWeeklyPickRequest;
import com.orangejd.server.dto.CareerProjectView;
import com.orangejd.server.dto.CareerWeeklyPickView;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.service.CareerProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/career-projects")
public class AdminCareerProjectController {
    private final CareerProjectService service;
    private final IngestionCoordinatorService coordinator;

    public AdminCareerProjectController(CareerProjectService service, IngestionCoordinatorService coordinator) {
        this.service = service;
        this.coordinator = coordinator;
    }

    @GetMapping
    public Result<PageResult<CareerProjectView>> list(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.adminList(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<CareerProjectView> detail(@PathVariable Long id) { return Result.success(service.detail(id, true)); }

    @PutMapping("/{id}/status")
    public Result<?> status(@PathVariable Long id, @Valid @RequestBody BeginnerProjectStatusRequest request) {
        service.updateStatus(id, request.status(), request.hiddenReason());
        return Result.success();
    }

    @PostMapping("/{id}/verify")
    public Result<Map<String, Object>> verify(@PathVariable Long id) {
        IngestionRunService.StartResult result = coordinator.start(
                IngestionCoordinatorService.CAREER_VERIFY, "ADMIN", null, Map.of("projectId", id));
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        IngestionRunService.StartResult result = coordinator.start(
                IngestionCoordinatorService.CAREER_DISCOVERY, "ADMIN", null, Map.of());
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @GetMapping("/weekly-picks")
    public Result<CareerWeeklyPickView> weekly() { return Result.success(service.weeklyAdminView()); }

    @PutMapping("/weekly-picks")
    public Result<CareerWeeklyPickView> replace(@Valid @RequestBody BeginnerWeeklyPickRequest request) {
        return Result.success(service.replaceWeeklyPicks(request.projectIds()));
    }

    @PostMapping("/weekly-picks/restore-algorithm")
    public Result<CareerWeeklyPickView> restore() { return Result.success(service.restoreAlgorithmWeeklyPicks()); }
}
