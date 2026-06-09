package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.IBrowseHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/history")
public class BrowseHistoryController {

    @Autowired private IBrowseHistoryService browseHistoryService;

    @PostMapping
    public Result<?> record(@RequestParam String targetType, @RequestParam Long targetId) {
        browseHistoryService.record(targetType, targetId);
        return Result.success();
    }

    @GetMapping
    public Result<?> myHistory(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(browseHistoryService.getHistory(page, size));
    }
}
