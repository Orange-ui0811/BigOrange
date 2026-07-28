package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.server.service.LaunchCheckService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/app/launch-check")
public class AppLaunchCheckController {

    private final LaunchCheckService service;

    public AppLaunchCheckController(LaunchCheckService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public Result<?> config() {
        return Result.success(service.config());
    }

    @PostMapping("/projects")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        return Result.success(service.create(requireAccountId(), body));
    }

    @GetMapping("/projects")
    public Result<?> history(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(service.history(requireAccountId(), limit));
    }

    @GetMapping("/projects/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.success(service.detail(requireAccountId(), id));
    }

    @PostMapping("/projects/{id}/stages/{stageNo}/generate")
    public Result<?> generate(@PathVariable Long id, @PathVariable int stageNo) {
        return Result.success(service.generate(requireAccountId(), id, stageNo));
    }

    @DeleteMapping("/projects/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(requireAccountId(), id);
        return Result.success();
    }

    private Long requireAccountId() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId <= 0) throw new com.orangejd.common.exception.BaseException("请先登录");
        return accountId;
    }
}
