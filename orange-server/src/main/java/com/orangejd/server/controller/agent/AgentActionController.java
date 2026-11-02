package com.orangejd.server.controller.agent;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.interceptor.AgentDeviceInterceptor;
import com.orangejd.server.service.AgentActionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/agent/actions")
public class AgentActionController {
    private final AgentActionService service;

    public AgentActionController(AgentActionService service) { this.service = service; }

    @PostMapping("/prepare")
    public Result<?> prepare(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(service.prepare(accountId(), credentialId(request),
                String.valueOf(request.getAttribute(AgentDeviceInterceptor.SCOPES_ATTRIBUTE)), body));
    }

    @PostMapping("/{id}/confirm")
    public Result<?> confirm(@PathVariable String id, HttpServletRequest request) {
        return Result.success(service.confirm(accountId(), credentialId(request), id));
    }

    @GetMapping("/status")
    public Result<?> status(@RequestParam(defaultValue = "") String taskId,
                            HttpServletRequest request) {
        return Result.success(service.status(accountId(), credentialId(request), taskId));
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable String id, HttpServletRequest request) {
        return Result.success(service.cancel(accountId(), credentialId(request), id));
    }

    private Long accountId() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new BaseException("设备未授权");
        return id;
    }

    private Long credentialId(HttpServletRequest request) {
        Object id = request.getAttribute(AgentDeviceInterceptor.CREDENTIAL_ID_ATTRIBUTE);
        if (!(id instanceof Number number)) throw new BaseException("设备未授权");
        return number.longValue();
    }
}
