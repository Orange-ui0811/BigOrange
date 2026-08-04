package com.orangejd.server.controller.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.service.AiRecordMetrics;
import com.orangejd.server.service.KnowledgeGraphService;
import com.orangejd.server.service.TrendAnalysisService;
import com.orangejd.server.service.TrendProfileAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/app/trend")
public class AppTrendController {

    @Autowired
    private KnowledgeGraphService graphService;

    @Autowired
    private TrendAnalysisService analysisService;

    @Autowired
    private TrendProfileAnalysisService profileAnalysisService;

    @Autowired
    private com.orangejd.server.mapper.TrendHistoryMapper trendHistoryMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiRecordMetrics recordMetrics;

    @Autowired
    private com.orangejd.common.properties.JwtProperties jwtProperties;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping("/graph")
    public Result<Map<String, Object>> graph() {
        return Result.success(graphService.buildCompareGraph());
    }

    @PostMapping("/analyze")
    public Result<?> analyze(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String question = text(body.get("question"), 2_000);

        if (question.isEmpty()) {
            return Result.error("请输入分析问题");
        }
        if (!acquireRateLimit(request)) return Result.error("请求过于频繁，请 10 秒后重试");

        Map<String, Object> analysis = analysisService.analyze(question);
        if (analysis.containsKey("error")) {
            return Result.error((String) analysis.get("error"));
        }
        return Result.success(analysis);
    }

    @PostMapping("/analyze-v2")
    public Result<?> analyzeV2(@RequestBody TrendProfileDTO profile, HttpServletRequest request) {
        setCurrentUserFromToken(request);
        if (profile.getTopics() == null || profile.getTopics().isEmpty())
            return Result.error("请至少选择一个技术方向");
        if (!acquireRateLimit(request)) return Result.error("请求过于频繁，请 10 秒后重试");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("topics", profile.getTopics());
        parameters.put("purpose", profile.getPurpose());
        parameters.put("experience", profile.getExperience());
        parameters.put("languages", profile.getLanguages());
        parameters.put("timeCommitment", profile.getTimeCommitment());
        parameters.put("customTopics", profile.getCustomTopics());
        parameters.put("customPurpose", profile.getCustomPurpose());
        parameters.put("customExp", profile.getCustomExp());
        parameters.put("customLang", profile.getCustomLang());
        parameters.put("customTime", profile.getCustomTime());
        parameters.put("useRag", profile.getUseRag());
        parameters.put("preferBeginnerProjects", profile.getPreferBeginnerProjects());
        parameters.put("preferCareerProjects", profile.getPreferCareerProjects());
        return Result.success(profileAnalysisService.analyze(BaseContext.getCurrentId(), parameters));
    }

    // ---- 查询历史（需登录）----

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(
            @RequestParam(defaultValue = "100") int limit) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        List<TrendHistory> list = trendHistoryMapper.selectByBrowserId(accountId,
                Math.max(1, Math.min(limit, 200)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TrendHistory h : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("topics", parseStringList(h.getTopics()));
            m.put("purpose", h.getPurpose());
            m.put("experience", h.getExperience());
            m.put("languages", parseStringList(h.getLanguages()));
            m.put("timeCommitment", h.getTimeCommitment());
            m.put("customTopics", h.getCustomTopics());
            m.put("customPurpose", h.getCustomPurpose());
            m.put("customExp", h.getCustomExp());
            m.put("customLang", h.getCustomLang());
            m.put("customTime", h.getCustomTime());
            m.put("useRag", !Boolean.FALSE.equals(h.getUseRag()));
            m.put("preferBeginnerProjects", Boolean.TRUE.equals(h.getPreferBeginnerProjects()));
            m.put("preferCareerProjects", Boolean.TRUE.equals(h.getPreferCareerProjects()));
            m.put("createTime", h.getCreateTime() != null ? h.getCreateTime().toString() : "");
            try {
                m.put("result", objectMapper.readValue(h.getResultJson(), Object.class));
                m.put("corrupted", false);
            } catch (Exception e) {
                m.put("result", null);
                m.put("corrupted", true);
                recordMetrics.corrupted("trend");
            }
            result.add(m);
        }
        return Result.success(result);
    }

    @PostMapping("/history/save")
    public Result<?> saveHistory(@RequestBody Map<String, Object> body) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        try {
            TrendHistory h = new TrendHistory();
            h.setAccountId(accountId);
            h.setTopics(objectMapper.writeValueAsString(stringList(body.get("topics"), 20, 100)));
            h.setPurpose(text(body.get("purpose"), 200));
            h.setExperience(text(body.get("experience"), 200));
            h.setLanguages(objectMapper.writeValueAsString(stringList(body.get("languages"), 20, 100)));
            h.setTimeCommitment(text(body.get("timeCommitment"), 200));
            h.setCustomTopics(text(body.get("customTopics"), 200));
            h.setCustomPurpose(text(body.get("customPurpose"), 200));
            h.setCustomExp(text(body.get("customExp"), 200));
            h.setCustomLang(text(body.get("customLang"), 200));
            h.setCustomTime(text(body.get("customTime"), 200));
            h.setUseRag(!Boolean.FALSE.equals(body.get("useRag")));
            h.setPreferBeginnerProjects(Boolean.TRUE.equals(body.get("preferBeginnerProjects")));
            h.setPreferCareerProjects(Boolean.TRUE.equals(body.get("preferCareerProjects")));
            Object rawResult = body.containsKey("result") ? body.get("result") : body.get("resultString");
            Object parsedResult = rawResult instanceof String value
                    ? objectMapper.readValue(value, Object.class) : rawResult;
            if (parsedResult == null) throw new BaseException("趋势分析结果不能为空");
            String resultJson = objectMapper.writeValueAsString(parsedResult);
            if (resultJson.length() > 2_000_000) throw new BaseException("趋势分析结果过长");
            h.setResultJson(resultJson);
            trendHistoryMapper.insert(h);
            recordMetrics.success("trend", "save");
            return Result.success(Collections.singletonMap("id", h.getId()));
        } catch (BaseException e) {
            recordMetrics.failure("trend", "save");
            throw e;
        } catch (Exception e) {
            recordMetrics.failure("trend", "save");
            throw new BaseException("趋势记录格式不正确");
        }
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        if (trendHistoryMapper.deleteById(id, accountId) == 0) return Result.error("记录不存在");
        recordMetrics.success("trend", "delete");
        return Result.success();
    }

    @DeleteMapping("/history/clear")
    public Result<?> clearHistory() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        trendHistoryMapper.deleteByBrowserId(accountId);
        recordMetrics.success("trend", "clear");
        return Result.success();
    }

    private boolean acquireRateLimit(HttpServletRequest request) {
        if (redisTemplate == null) return true;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        else ip = ip.split(",", 2)[0].trim();
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                "rate:trend:analyze:" + ip, "1", 10, TimeUnit.SECONDS));
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            String normalized = value.trim().replaceAll("^\\[|]$", "");
            if (normalized.isBlank()) return List.of();
            return Arrays.stream(normalized.split("[,，、/]"))
                    .map(String::trim)
                    .map(item -> item.replaceAll("^\"|\"$", ""))
                    .filter(item -> !item.isBlank())
                    .limit(20)
                    .toList();
        }
    }

    private List<String> stringList(Object value, int maxItems, int maxLength) {
        if (value == null) return List.of();
        List<?> source = value instanceof List<?> list ? list : List.of(value);
        if (source.size() > maxItems) throw new BaseException("选择项过多");
        return source.stream().map(item -> text(item, maxLength))
                .filter(item -> !item.isBlank()).toList();
    }

    private String text(Object value, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > maxLength) throw new BaseException("输入内容过长");
        return result;
    }

    /** 从 App-Token 请求头解析用户 ID 并设置到 BaseContext（公开端点专用） */
    private void setCurrentUserFromToken(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getAppTokenName());
        if (token != null && !token.isEmpty()) {
            try {
                io.jsonwebtoken.Claims claims = com.orangejd.common.utils.JwtUtil
                    .parseJwt(jwtProperties.getAppSecretKey(), token);
                Long id = claims.get("id", Long.class);
                if (id != null) {
                    com.orangejd.common.context.BaseContext.setCurrentId(id);
                }
            } catch (Exception ignored) {
                // token 无效或过期，不设置用户身份，不扣费
            }
        }
    }
}
