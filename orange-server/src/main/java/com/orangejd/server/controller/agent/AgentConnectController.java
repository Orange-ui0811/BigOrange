package com.orangejd.server.controller.agent;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.AgentDeviceAuthService;
import com.orangejd.server.service.RateLimitService;
import com.orangejd.server.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/agent/connect")
public class AgentConnectController {

    private final AgentDeviceAuthService service;
    private final RateLimitService rateLimitService;

    public AgentConnectController(AgentDeviceAuthService service, RateLimitService rateLimitService) {
        this.service = service;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/exchange")
    public Result<?> exchange(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        if (!rateLimitService.allow("agent-link:ip:" + ip, 10, 60)) {
            return Result.error("绑定尝试过于频繁，请稍后再试");
        }
        String code = String.valueOf(body.getOrDefault("code", ""));
        String name = String.valueOf(body.getOrDefault("deviceName", ""));
        return Result.success(service.exchange(code, name));
    }
}
