package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/app/config")
public class AppConfigController {

    private static final String TRADE_MODE_KEY = "system:trade-mode";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/trade-mode")
    public Result<Map<String, Boolean>> getTradeMode() {
        String val = redisTemplate.opsForValue().get(TRADE_MODE_KEY);
        boolean enabled = !"false".equals(val);
        return Result.success(Map.of("enabled", enabled));
    }
}
