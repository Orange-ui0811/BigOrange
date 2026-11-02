package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.foundation.ingestion.IngestionCoordinatorService;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/ingestion/runs")
public class AdminIngestionController {
    private final IngestionRunService runService;
    private final IngestionCoordinatorService coordinator;

    public AdminIngestionController(IngestionRunService runService,
                                    IngestionCoordinatorService coordinator) {
        this.runService = runService;
        this.coordinator = coordinator;
    }

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String pipelineType,
                          @RequestParam(required = false) String status) {
        return Result.success(runService.page(pipelineType, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Map<String, Object> detail = runService.detail(id);
        return detail == null ? Result.error("运行记录不存在") : Result.success(detail);
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        String pipelineType = String.valueOf(body.getOrDefault("pipelineType", ""));
        IngestionRunService.StartResult result = coordinator.start(
                pipelineType, "ADMIN", null, body);
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @PostMapping("/{id}/retry")
    public Result<?> retry(@PathVariable Long id) {
        IngestionRunService.StartResult result = coordinator.retry(id);
        return Result.success(Map.of("runId", result.runId(), "started", result.started()));
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        return Result.success(Map.of("cancelled", coordinator.cancel(id)));
    }
}
