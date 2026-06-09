package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/statistics")
public class StatisticsController {

    @Autowired private AccountMapper accountMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private RequirementMapper requirementMapper;
    @Autowired private OrderMapper orderMapper;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", accountMapper.countAll());
        data.put("totalWorks", workMapper.countAll(""));
        data.put("totalRequirements", requirementMapper.countAll(""));
        data.put("pendingAudits", workMapper.countAll("PENDING_REVIEW") + requirementMapper.countAll("PENDING_REVIEW"));
        data.put("totalOrders", orderMapper.countAllOrders());
        data.put("todayRevenue", 0); // Phase 7+ SQL聚合
        return Result.success(data);
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "30") int days) {
        // Phase 7+ 可扩展：按天查询新增数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", days);
        data.put("works", new ArrayList<>());
        data.put("users", new ArrayList<>());
        return Result.success(data);
    }

    @GetMapping("/top-works")
    public Result<List<Map<String, Object>>> topWorks(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(workMapper.selectTopByFavorites(limit));
    }

    @GetMapping("/top-creators")
    public Result<List<Map<String, Object>>> topCreators(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(accountMapper.selectTopCreators(limit));
    }
}
