package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.service.BeginnerProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/beginner-projects")
public class AppBeginnerProjectController {
    private final BeginnerProjectService service;

    public AppBeginnerProjectController(BeginnerProjectService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<BeginnerProjectView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.success(service.publicList(keyword, language, difficulty, topic, page, size));
    }

    @GetMapping("/weekly")
    public Result<List<BeginnerProjectView>> weekly() {
        return Result.success(service.weeklyPicks());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(service.detail(id, false));
    }
}
