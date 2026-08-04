package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.result.Result;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.server.mapper.PlanHistoryMapper;
import com.orangejd.server.service.PlanGenerationService;
import com.orangejd.server.service.AiRecordMetrics;
import com.orangejd.server.service.PricingService;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    @Autowired
    private PricingService pricingService;
    @Autowired
    private AiRecordMetrics recordMetrics;

    @PostMapping("/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        setCurrentUserFromToken(request);
        Long accountId = BaseContext.getCurrentId();
        Map<String, Object> result = generationService.generate(accountId, body);
        if (accountId != null && accountId > 0 && result.get("content") != null) {
            int generatedPhase = number(result.get("phase"), 0L).intValue() + 1;
            if (generatedPhase >= 2 && generatedPhase <= 5) {
                Map<String, Object> snapshot = new LinkedHashMap<>(body);
                snapshot.put("phase" + generatedPhase, result.get("content"));
                result.put("historyId", upsertHistory(accountId, snapshot));
            }
        }
        return Result.success(result);
    }

    @GetMapping("/config")
    public Result<?> config() {
        return Result.success(Map.of(
                "stageCost", generationService.cost(),
                "jobMatchCost", pricingService.getCost("ai:match")));
    }

    @GetMapping("/history")
    public Result<?> history(@RequestParam(defaultValue = "100") int limit) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        return Result.success(planHistoryMapper.selectByAccountId(
                accountId, Math.max(1, Math.min(limit, 200))));
    }

    @PostMapping("/history/save")
    public Result<?> saveHistory(@RequestBody Map<String, Object> body) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        try {
            Long id = upsertHistory(accountId, body);
            recordMetrics.success("plan", "save");
            return Result.success(Collections.singletonMap("id", id));
        } catch (BaseException e) {
            recordMetrics.failure("plan", "save");
            throw e;
        } catch (Exception e) {
            recordMetrics.failure("plan", "save");
            throw new BaseException("方案记录保存失败");
        }
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        if (planHistoryMapper.deleteById(id, accountId) == 0) return Result.error("记录不存在");
        recordMetrics.success("plan", "delete");
        return Result.success();
    }

    @DeleteMapping("/history/clear")
    public Result<?> clearHistory() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        planHistoryMapper.deleteByAccountId(accountId);
        recordMetrics.success("plan", "clear");
        return Result.success();
    }

    private String text(Object value, int maxLength, String fieldName) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > maxLength) throw new BaseException(fieldName + "内容过长");
        return result;
    }

    private Long upsertHistory(Long accountId, Map<String, Object> body) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("accountId", accountId);
        record.put("summary", text(body.get("summary"), 10_000, "方案摘要"));
        for (int phase = 1; phase <= 6; phase++) {
            String key = "phase" + phase;
            record.put(key, text(body.get(key), 2_000_000, "方案阶段"));
        }
        record.put("contentHash", contentHash(record));

        Long historyId = number(body.get("historyId"), null);
        if (historyId == null) historyId = number(body.get("id"), null);
        if (historyId != null && historyId > 0) {
            record.put("id", historyId);
            if (planHistoryMapper.updateOwned(record) > 0) return historyId;
            record.remove("id");
        }
        planHistoryMapper.insert(record);
        return number(record.get("id"), null);
    }

    private Long number(Object value, Long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String contentHash(Map<String, Object> record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String key : new String[]{"summary", "phase1", "phase2", "phase3",
                    "phase4", "phase5", "phase6"}) {
                byte[] value = String.valueOf(record.get(key)).getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (value.length >>> 24));
                digest.update((byte) (value.length >>> 16));
                digest.update((byte) (value.length >>> 8));
                digest.update((byte) value.length);
                digest.update(value);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
