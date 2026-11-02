package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.AiCompetition;
import com.orangejd.server.dto.CompetitionRequest;
import com.orangejd.server.service.CompetitionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/competition")
public class CompetitionController {

    private final CompetitionService service;

    public CompetitionController(CompetitionService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "ACTIVE") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        boolean history = "HISTORY".equalsIgnoreCase(scope);
        PageResult<AiCompetition> source = service.publicList(history, page, size);
        List<Map<String, Object>> records = source.getRecords().stream().map(this::publicView).toList();
        return Result.success(new PageResult<>(source.getTotal(), records));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@Valid @RequestBody CompetitionRequest body,
                                               HttpServletRequest request) {
        Long id = service.submit(body, BaseContext.getCurrentId(), clientIp(request));
        return Result.success(Map.of("id", id));
    }

    private Map<String, Object> publicView(AiCompetition competition) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = competition.getStartTime();
        LocalDateTime endTime = competition.getEndTime();
        String phase = endTime != null && endTime.isBefore(now)
                ? "ENDED"
                : startTime != null && startTime.isAfter(now)
                ? "UPCOMING"
                : startTime == null && endTime == null ? "UNSCHEDULED" : "ONGOING";
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", competition.getId());
        view.put("name", competition.getName());
        view.put("sourceUrl", competition.getSourceUrl());
        view.put("summary", competition.getSummary());
        view.put("organizer", competition.getOrganizer());
        view.put("startTime", competition.getStartTime());
        view.put("endTime", competition.getEndTime());
        view.put("phase", phase);
        return view;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
