package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.server.dto.BeginnerProjectStatusRequest;
import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.BeginnerWeeklyPickRequest;
import com.orangejd.server.dto.BeginnerWeeklyPickView;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import com.orangejd.server.service.BeginnerProjectService;
import com.orangejd.server.service.ProjectKnowledgeSyncService;
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
@RequestMapping("/admin/beginner-projects")
public class AdminBeginnerProjectController {
    private final BeginnerProjectService service;
    private final ProjectKnowledgeSyncService projectKnowledgeSyncService;
    private final IngestionCoordinatorService coordinator;

    public AdminBeginnerProjectController(BeginnerProjectService service,
                                          ProjectKnowledgeSyncService projectKnowledgeSyncService,
                                          IngestionCoordinatorService coordinator) {
        this.service = service;
        this.projectKnowledgeSyncService = projectKnowledgeSyncService;
        this.coordinator = coordinator;
    }

    @GetMapping
    public Result<PageResult<BeginnerProjectView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.adminList(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(service.detail(id, true));
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id,
                                  @Valid @RequestBody BeginnerProjectStatusRequest request) {
        service.updateStatus(id, request.status(), request.hiddenReason());
        projectKnowledgeSyncService.syncBeginner(id);
        return Result.success();
    }

    @PostMapping("/{id}/verify")
    public Result<Map<String, Object>> verify(@PathVariable Long id) {
        IngestionRunService.StartResult result = coordinator.start(
                IngestionCoordinatorService.BEGINNER_VERIFY, "ADMIN", null, Map.of("projectId", id));
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @PostMapping("/weekly-picks/rebuild")
    public Result<Map<String, Object>> rebuildWeeklyPicks() {
        IngestionRunService.StartResult result = coordinator.start(
                IngestionCoordinatorService.BEGINNER_WEEKLY_PICK, "ADMIN", null, Map.of());
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @GetMapping("/weekly-picks")
    public Result<BeginnerWeeklyPickView> weeklyPicks() {
        return Result.success(service.weeklyPickAdminView());
    }

    @PutMapping("/weekly-picks")
    public Result<BeginnerWeeklyPickView> replaceWeeklyPicks(
            @Valid @RequestBody BeginnerWeeklyPickRequest request) {
        return Result.success(service.replaceWeeklyPicks(request.projectIds()));
    }

    @PostMapping("/weekly-picks/restore-algorithm")
    public Result<BeginnerWeeklyPickView> restoreAlgorithmWeeklyPicks() {
        return Result.success(service.restoreAlgorithmWeeklyPicks());
    }
}
