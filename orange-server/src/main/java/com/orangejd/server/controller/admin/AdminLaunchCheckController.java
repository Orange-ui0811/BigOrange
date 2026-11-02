package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.LaunchCheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/launch-check")
public class AdminLaunchCheckController {

    private final LaunchCheckService service;

    public AdminLaunchCheckController(LaunchCheckService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.adminPage(page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.success(service.adminDetail(id));
    }
}
