package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.ILeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/app/leaderboard")
public class AppLeaderboardController {

    @Autowired
    private ILeaderboardService leaderboardService;

    @GetMapping("/works")
    public Result<?> works(@RequestParam(required = false) Long categoryId,
                           @RequestParam(defaultValue = "all") String period,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(leaderboardService.getWorks(categoryId, period, page, size));
    }

    @GetMapping("/creators")
    public Result<?> creators(@RequestParam(defaultValue = "all") String period,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return Result.success(leaderboardService.getCreators(period, page, size));
    }

    @GetMapping("/github")
    public Result<?> github(@RequestParam(required = false) String topicName,
                            @RequestParam(defaultValue = "all") String period,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(leaderboardService.getGithubRepos(topicName, period, page, size));
    }
}
