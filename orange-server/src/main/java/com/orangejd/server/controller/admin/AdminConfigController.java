package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/config")
@lombok.RequiredArgsConstructor
public class AdminConfigController {

    private static final String TRADE_MODE_KEY = "system:trade-mode";

    private final StringRedisTemplate redisTemplate;

    @PutMapping("/trade-mode")
    public Result<?> setTradeMode(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.getOrDefault("enabled", true);
        redisTemplate.opsForValue().set(TRADE_MODE_KEY, enabled.toString());
        return Result.success(Map.of("enabled", enabled));
    }

    @GetMapping("/trade-mode")
    public Result<Map<String, Boolean>> getTradeMode() {
        String val = redisTemplate.opsForValue().get(TRADE_MODE_KEY);
        boolean enabled = !"false".equals(val); // 默认 true（开启）
        return Result.success(Map.of("enabled", enabled));
    }
}
