package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.service.OpenSourceContributionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/app/open-source")
public class AppOpenSourceController {

    private final OpenSourceContributionService service;

    public AppOpenSourceController(OpenSourceContributionService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public Result<?> config() { return Result.success(service.config()); }

    @GetMapping("/issues")
    public Result<?> issues(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(required = false) String difficulty,
                            @RequestParam(required = false) String participationStatus,
                            @RequestParam(required = false) String tech,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer updatedDays) {
        return Result.success(service.list(page, size, difficulty, participationStatus,
                tech, keyword, updatedDays));
    }

    @GetMapping("/ticker")
    public Result<?> ticker() { return Result.success(service.ticker()); }

    @GetMapping("/issues/{id}")
    public Result<?> detail(@PathVariable Long id) { return Result.success(service.detail(id)); }

    @PostMapping("/issues/{id}/verify")
    public Result<?> verify(@PathVariable Long id) { return Result.success(service.verify(id)); }

    @PostMapping("/match")
    public Result<?> match(@RequestBody Map<String, Object> body) {
        return Result.success(service.match(requireAccountId(), body));
    }

    @PostMapping("/issues/{id}/job-match")
    public Result<?> jobMatch(@PathVariable Long id) {
        return Result.success(service.jobMatch(requireAccountId(), id));
    }

    @PostMapping("/issues/{id}/guide")
    public Result<?> guide(@PathVariable Long id) {
        return Result.success(service.generateGuide(requireAccountId(), id));
    }

    @GetMapping("/guides")
    public Result<?> guides() { return Result.success(service.guides(requireAccountId())); }

    @GetMapping("/guides/{id}")
    public Result<?> guideDetail(@PathVariable Long id) {
        return Result.success(service.guide(requireAccountId(), id));
    }

    private Long requireAccountId() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId <= 0) throw new BaseException("请先登录");
        return accountId;
    }
}
