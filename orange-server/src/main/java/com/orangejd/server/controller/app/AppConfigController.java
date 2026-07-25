package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.service.PricingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/app/config")
public class AppConfigController {

    private static final String TRADE_MODE_KEY = "system:trade-mode";

    private final StringRedisTemplate redisTemplate;
    private final EpayProperties epayProperties;
    private final PricingService pricingService;

    public AppConfigController(StringRedisTemplate redisTemplate,
                               EpayProperties epayProperties,
                               PricingService pricingService) {
        this.redisTemplate = redisTemplate;
        this.epayProperties = epayProperties;
        this.pricingService = pricingService;
    }

    @GetMapping("/trade-mode")
    public Result<Map<String, Boolean>> getTradeMode() {
        String val = redisTemplate.opsForValue().get(TRADE_MODE_KEY);
        boolean enabled = !"false".equals(val);
        boolean orderPaymentEnabled = epayProperties.isConfigured() && epayProperties.isOrderPaymentsEnabled();
        return Result.success(Map.of("enabled", enabled, "orderPaymentEnabled", orderPaymentEnabled));
    }

    @GetMapping("/rewards")
    public Result<Map<String, Integer>> getRewardPricing() {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        rewards.put("work", pricingService.getReward(PricingService.REWARD_WORK_APPROVED));
        rewards.put("job", pricingService.getReward(PricingService.REWARD_JOB_APPROVED));
        rewards.put("competition", pricingService.getReward(PricingService.REWARD_COMPETITION_APPROVED));
        return Result.success(rewards);
    }
}
