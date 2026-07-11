package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.pojo.entity.PricingChangeLog;
import com.orangejd.server.service.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/pricing")
public class AdminPricingController {

    @Autowired
    private PricingService pricingService;

    /** 获取全部定价 + 最近10条变更日志 */
    @GetMapping
    public Result<Map<String, Object>> listAll() {
        List<AiServicePricing> pricing = pricingService.listAll();
        List<PricingChangeLog> logs = pricingService.getRecentLogs(10);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pricing", pricing);
        result.put("changeLogs", logs);
        return Result.success(result);
    }

    /** 更新单条定价 */
    @PostMapping("/save")
    public Result<?> save(@RequestBody Map<String, Object> body) {
        String serviceKey = (String) body.get("serviceKey");
        Integer newCost = body.get("newCost") instanceof Integer
            ? (Integer) body.get("newCost")
            : Integer.valueOf(body.get("newCost").toString());
        String serviceName = (String) body.get("serviceName");

        if (serviceKey == null || newCost == null) {
            return Result.error("缺少 serviceKey 或 newCost 参数");
        }

        Long adminId = BaseContext.getCurrentId();
        if (adminId == null || adminId <= 0) {
            adminId = 0L; // 无上下文时记为 0
        }

        try {
            int updated = pricingService.updateCost(serviceKey, newCost, adminId, serviceName);
            return Result.success(Map.of(
                "serviceKey", serviceKey,
                "newCost", updated,
                "message", "定价已更新，即时生效"
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
