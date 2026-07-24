package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.result.Result;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.server.mapper.PlanHistoryMapper;
import com.orangejd.server.service.PlanGenerationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/app/plan")
public class AppPlanController {

    @Autowired
    private PlanHistoryMapper planHistoryMapper;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private PlanGenerationService generationService;

    @PostMapping("/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        setCurrentUserFromToken(request);
        return Result.success(generationService.generate(BaseContext.getCurrentId(), body));
    }

    @GetMapping("/history")
    public Result<?> history() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        return Result.success(planHistoryMapper.selectByAccountId(accountId, 20));
    }

    @PostMapping("/history/save")
    public Result<?> saveHistory(@RequestBody Map<String, Object> body) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("accountId", accountId);
        record.put("summary", body.getOrDefault("summary", ""));
        record.put("phase1", body.getOrDefault("phase1", ""));
        record.put("phase2", body.getOrDefault("phase2", ""));
        record.put("phase3", body.getOrDefault("phase3", ""));
        record.put("phase4", body.getOrDefault("phase4", ""));
        record.put("phase5", body.getOrDefault("phase5", ""));
        record.put("phase6", body.getOrDefault("phase6", ""));
        planHistoryMapper.insert(record);
        return Result.success(Collections.singletonMap("id", record.get("id")));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        planHistoryMapper.deleteById(id, accountId);
        return Result.success();
    }

    @DeleteMapping("/history/clear")
    public Result<?> clearHistory() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        planHistoryMapper.deleteByAccountId(accountId);
        return Result.success();
    }

    private void setCurrentUserFromToken(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getAppTokenName());
        if (token == null || token.isEmpty()) return;
        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getAppSecretKey(), token);
            Long id = claims.get("id", Long.class);
            if (id != null) BaseContext.setCurrentId(id);
        } catch (Exception ignored) {
            // JWT interceptor remains the authority for protected routes.
        }
    }
}
