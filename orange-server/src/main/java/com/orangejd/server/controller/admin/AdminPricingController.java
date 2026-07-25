package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.pojo.entity.PricingChangeLog;
import com.orangejd.server.service.PricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/pricing")
public class AdminPricingController {

    private final PricingService pricingService;

    public AdminPricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public Result<Map<String, Object>> listAll() {
        List<AiServicePricing> pricing = pricingService.listAll();
        List<PricingChangeLog> logs = pricingService.getRecentLogs(10);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pricing", pricing);
        result.put("changeLogs", logs);
        return Result.success(result);
    }

    @PostMapping("/save")
    public Result<?> save(@RequestBody PricingUpdateRequest request) {
        if (request == null || request.serviceKey() == null || request.newCost() == null) {
            return Result.error("缺少 serviceKey 或 newCost 参数");
        }

        Long adminId = BaseContext.getCurrentId();
        if (adminId == null || adminId <= 0) {
            return Result.error("管理员登录状态无效");
        }

        try {
            int updated = pricingService.updateCost(
                    request.serviceKey(),
                    request.newCost(),
                    adminId,
                    request.serviceName()
            );
            return Result.success(Map.of(
                    "serviceKey", request.serviceKey(),
                    "newCost", updated,
                    "message", "定价已更新并立即生效"
            ));
        } catch (IllegalArgumentException exception) {
            return Result.error(exception.getMessage());
        }
    }

    public record PricingUpdateRequest(String serviceKey, Integer newCost, String serviceName) {}
}
