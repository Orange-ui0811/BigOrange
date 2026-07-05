package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.server.mapper.QuotaLogMapper;
import com.orangejd.server.service.QuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/app/quota")
public class AppQuotaController {

    @Autowired private QuotaService quotaService;
    @Autowired private QuotaLogMapper quotaLogMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

    @PostMapping("/redeem")
    public Result<Long> redeem(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) return Result.error("请输入充值码");

        Long accountId = BaseContext.getCurrentId();

        // 频率限制：同一 IP 每分钟 5 次
        String ip = getClientIp(request);
        if (redisTemplate != null) {
            String limitKey = "rate:redeem:" + ip;
            String count = redisTemplate.opsForValue().get(limitKey);
            int cnt = count != null ? Integer.parseInt(count) : 0;
            if (cnt >= 5) return Result.error("操作频繁，请稍后重试");
            if (cnt == 0) redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);
            else redisTemplate.opsForValue().increment(limitKey);
        }

        int balance = quotaService.redeemCode(accountId, code);
        return Result.success((long) balance);
    }

    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() {
        Long accountId = BaseContext.getCurrentId();
        long balance = quotaService.getBalance(accountId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", balance);
        return Result.success(result);
    }

    @GetMapping("/log")
    public Result<List<QuotaLog>> log(@RequestParam(defaultValue = "20") int limit) {
        Long accountId = BaseContext.getCurrentId();
        return Result.success(quotaLogMapper.selectByAccountId(accountId, Math.min(limit, 100)));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
