package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.server.service.QuotaLogService;
import com.orangejd.server.service.QuotaService;
import com.orangejd.server.service.RateLimitService;
import com.orangejd.server.util.ClientIpResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/app/quota")
@lombok.RequiredArgsConstructor
public class AppQuotaController {

    private final QuotaService quotaService;
    private final QuotaLogService quotaLogMapper;
    private final RateLimitService rateLimitService;

    @PostMapping("/redeem")
    public Result<Long> redeem(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) return Result.error("请输入充值码");

        Long accountId = BaseContext.getCurrentId();

        // 频率限制：同一 IP 每分钟 5 次
        String ip = ClientIpResolver.resolve(request);
        if (!rateLimitService.allow("redeem:ip:" + ip, 5, 60)
                || !rateLimitService.allow("redeem:account:" + accountId, 5, 60))
            return Result.error("操作频繁，请稍后重试");

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
        return Result.success(quotaLogMapper.selectByAccountId(accountId,
                com.orangejd.common.util.PageUtil.capSize(limit)));
    }

}
