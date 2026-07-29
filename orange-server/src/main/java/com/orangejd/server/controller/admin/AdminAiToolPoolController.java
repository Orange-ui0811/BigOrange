package com.orangejd.server.controller.admin;

import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.foundation.aitool.AiToolPoolProperties;
import com.orangejd.server.foundation.aitool.AiToolRouter;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/ai-tool-pool")
public class AdminAiToolPoolController {
    private final AiToolPoolProperties properties;
    private final AiToolRouter router;
    private final ExternalAiClient aiClient;

    public AdminAiToolPoolController(AiToolPoolProperties properties, AiToolRouter router,
                                     ExternalAiClient aiClient) {
        this.properties = properties;
        this.router = router;
        this.aiClient = aiClient;
    }

    @GetMapping
    public Result<?> overview() {
        Map<String, Object> result = new LinkedHashMap<>(router.runtime());
        result.put("configurationSource", "ENV");
        result.put("message", "Key 原文只从服务器环境读取，接口、日志和数据库均不返回");
        return Result.success(result);
    }

    @GetMapping("/runtime")
    public Result<?> runtime() {
        return Result.success(router.runtime());
    }

    @GetMapping("/calls")
    public Result<?> calls(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(router.recentCalls(limit));
    }

    @PostMapping("/slots/{slotNo}/disable")
    public Result<?> disable(@PathVariable int slotNo) {
        router.setSlotDisabled(slotNo, true);
        return Result.success();
    }

    @PostMapping("/slots/{slotNo}/enable")
    public Result<?> enable(@PathVariable int slotNo) {
        router.setSlotDisabled(slotNo, false);
        return Result.success();
    }

    @PostMapping("/slots/{slotNo}/reset")
    public Result<?> reset(@PathVariable int slotNo) {
        router.resetSlotStatus(slotNo);
        return Result.success();
    }

    @PostMapping("/slots/{slotNo}/test")
    public Result<?> test(@PathVariable int slotNo) {
        AiToolPoolProperties.Slot slot = properties.slots().stream()
                .filter(value -> value.number() == slotNo)
                .findFirst().orElseThrow(() -> new BaseException("该槽位未配置"));
        String content = aiClient.call(properties.config(slot),
                "你是连接测试助手。", "只回复：连接成功", 0, "admin-tool-slot-test");
        return Result.success(Map.of(
                "message", content,
                "slotNo", slotNo,
                "fingerprint", slot.fingerprint()));
    }
}
