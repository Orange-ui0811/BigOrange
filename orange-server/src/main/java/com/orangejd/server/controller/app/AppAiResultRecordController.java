package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.service.AiResultRecordService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/ai-records")
public class AppAiResultRecordController {
    private final AiResultRecordService service;

    public AppAiResultRecordController(AiResultRecordService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> page(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String businessType) {
        return Result.success(service.page(accountId(), page, size, businessType));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.success(service.detail(accountId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(accountId(), id);
        return Result.success();
    }

    private Long accountId() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId <= 0) throw new BaseException("请先登录");
        return accountId;
    }
}
