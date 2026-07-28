package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.service.AgentDeviceAuthService;
import com.orangejd.server.service.AgentActionService;
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
@RequestMapping("/app/agent-devices")
public class AppAgentDeviceController {

    private final AgentDeviceAuthService service;
    private final AgentActionService actionService;

    public AppAgentDeviceController(AgentDeviceAuthService service, AgentActionService actionService) {
        this.service = service;
        this.actionService = actionService;
    }

    @PostMapping("/link-code")
    public Result<?> createLinkCode(@RequestBody(required = false) Map<String, Object> body) {
        String name = body == null ? null : String.valueOf(body.getOrDefault("deviceName", ""));
        return Result.success(service.createLinkCode(accountId(), name));
    }

    @GetMapping
    public Result<?> list() {
        return Result.success(service.list(accountId()));
    }

    @GetMapping("/tasks")
    public Result<?> tasks(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(actionService.history(accountId(), limit));
    }

    @DeleteMapping("/tasks/{id}")
    public Result<?> deleteTask(@PathVariable String id) {
        actionService.deleteHistory(accountId(), id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> revoke(@PathVariable Long id) {
        service.revoke(accountId(), id);
        return Result.success();
    }

    private Long accountId() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new BaseException("请先登录");
        return id;
    }
}
