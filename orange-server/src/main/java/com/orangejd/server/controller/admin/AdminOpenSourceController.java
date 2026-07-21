package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.OpenSourceContributionService;
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
@RequestMapping("/admin/open-source")
public class AdminOpenSourceController {

    private final OpenSourceContributionService service;

    public AdminOpenSourceController(OpenSourceContributionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> overview(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Integer published,
                              @RequestParam(required = false) String translationStatus) {
        return Result.success(service.adminOverview(page, size, keyword, published, translationStatus));
    }

    @PostMapping("/repositories")
    public Result<?> createRepository(@RequestBody Map<String, Object> body) {
        return Result.success(service.saveRepository(null, body));
    }

    @PutMapping("/repositories/{id}")
    public Result<?> updateRepository(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.saveRepository(id, body));
    }

    @PostMapping("/repositories/{id}/refresh")
    public Result<?> refreshRepository(@PathVariable Long id) {
        return Result.success(Map.of("saved", service.refreshRepository(id)));
    }

    @PutMapping("/issues/{id}")
    public Result<?> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.updateIssue(id, body));
    }

    @PostMapping("/issues/{id}/verify")
    public Result<?> verifyIssue(@PathVariable Long id) {
        return Result.success(service.verify(id));
    }

    @PostMapping("/issues/{id}/translate")
    public Result<?> translateIssue(@PathVariable Long id) {
        service.translateIssue(id);
        return Result.success();
    }

    @PostMapping("/refresh/full")
    public Result<?> fullRefresh() {
        return Result.success(Map.of("started", service.triggerFullRefresh()));
    }

    @PostMapping("/refresh/verify")
    public Result<?> verifyRefresh() {
        return Result.success(Map.of("started", service.triggerVerification()));
    }
}
