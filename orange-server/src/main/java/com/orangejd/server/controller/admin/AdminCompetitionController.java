package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.AiCompetition;
import com.orangejd.server.dto.CompetitionRequest;
import com.orangejd.server.service.CompetitionMetadataService;
import com.orangejd.server.service.CompetitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/competition")
public class AdminCompetitionController {

    private final CompetitionService service;
    private final CompetitionMetadataService metadataService;

    public AdminCompetitionController(CompetitionService service,
                                      CompetitionMetadataService metadataService) {
        this.service = service;
        this.metadataService = metadataService;
    }

    @GetMapping("/list")
    public Result<PageResult<AiCompetition>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.success(service.adminList(status, page, size));
    }

    @GetMapping("/{id}")
    public Result<AiCompetition> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CompetitionRequest body) {
        Long id = service.createByAdmin(body, BaseContext.getCurrentId());
        return Result.success(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody CompetitionRequest body) {
        service.update(id, body);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        service.approve(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.reject(id, BaseContext.getCurrentId(), body == null ? null : body.get("reason"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/fetch-preview")
    public Result<Map<String, Object>> fetchPreview(@RequestBody Map<String, String> body) {
        return Result.success(metadataService.fetchPreview(body.get("sourceUrl")));
    }
}
