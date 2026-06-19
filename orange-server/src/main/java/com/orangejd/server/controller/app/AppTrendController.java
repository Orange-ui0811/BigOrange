package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.service.KnowledgeGraphService;
import com.orangejd.server.service.TrendAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    private com.orangejd.server.mapper.TrendHistoryMapper trendHistoryMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping("/graph")
    public Result<Map<String, Object>> graph() {
        return Result.success(graphService.buildCompareGraph());
    }

    @PostMapping("/analyze")
    public Result<?> analyze(@RequestBody Map<String, String> body, HttpServletRequest request) {
        // IP 限流：10 秒 1 次
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        String rateKey = "rate:trend:analyze:" + ip;

        if (redisTemplate != null) {
            String existing = redisTemplate.opsForValue().get(rateKey);
            if (existing != null) {
                return Result.error("请求过于频繁，请 10 秒后重试");
            }
            redisTemplate.opsForValue().set(rateKey, "1", 10, TimeUnit.SECONDS);
        }

        String apiEndpoint = body.getOrDefault("endpoint", "");
        String apiKey = body.getOrDefault("apiKey", "");
        String model = body.getOrDefault("model", "gpt-4o");
        String question = body.getOrDefault("question", "");

        if (apiEndpoint.isEmpty() || apiKey.isEmpty()) {
            return Result.error("请填写 API Endpoint 和 API Key");
        }
        if (question.isEmpty()) {
            return Result.error("请输入分析问题");
        }

        Map<String, Object> analysis = analysisService.analyze(apiEndpoint, apiKey, model, question);
        if (analysis.containsKey("error")) {
            return Result.error((String) analysis.get("error"));
        }
        return Result.success(analysis);
    }

    @PostMapping("/analyze-v2")
    public Result<?> analyzeV2(@RequestBody TrendProfileDTO profile, HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        String rateKey = "rate:trend:analyze:" + ip;
        if (redisTemplate != null) {
            if (redisTemplate.opsForValue().get(rateKey) != null)
                return Result.error("请求过于频繁，请 10 秒后重试");
            redisTemplate.opsForValue().set(rateKey, "1", 10, TimeUnit.SECONDS);
        }

        if (profile.getTopics() == null || profile.getTopics().isEmpty())
            return Result.error("请至少选择一个技术方向");

        Map<String, Object> result = analysisService.analyzeV2(
            profile.getTopics(), profile.getPurpose(), profile.getExperience(),
            profile.getLanguages(), profile.getTimeCommitment(),
            profile.getCustomTopics(), profile.getCustomPurpose(),
            profile.getCustomExp(), profile.getCustomLang(), profile.getCustomTime());

        if (result.containsKey("error"))
            return Result.error((String) result.get("error"));
        return Result.success(result);
    }

    // ---- 查询历史 ----

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam String browserId) {
        List<com.orangejd.pojo.entity.TrendHistory> list = trendHistoryMapper.selectByBrowserId(browserId, 20);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.orangejd.pojo.entity.TrendHistory h : list) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("topics", h.getTopics());
            m.put("purpose", h.getPurpose());
            m.put("experience", h.getExperience());
            m.put("languages", h.getLanguages());
            m.put("timeCommitment", h.getTimeCommitment());
            m.put("createTime", h.getCreateTime() != null ? h.getCreateTime().toString() : "");
            m.put("result", h.getResultJson());
            result.add(m);
        }
        return Result.success(result);
    }

    @PostMapping("/history/save")
    public Result<?> saveHistory(@RequestBody Map<String, Object> body) {
        com.orangejd.pojo.entity.TrendHistory h = new com.orangejd.pojo.entity.TrendHistory();
        h.setBrowserId((String) body.get("browserId"));
        h.setTopics(body.get("topics") != null ? body.get("topics").toString() : "");
        h.setPurpose((String) body.getOrDefault("purpose", ""));
        h.setExperience((String) body.getOrDefault("experience", ""));
        h.setLanguages(body.get("languages") != null ? body.get("languages").toString() : "");
        h.setTimeCommitment((String) body.getOrDefault("timeCommitment", ""));
        h.setResultJson(body.get("result") != null ? body.get("result").toString() : "");
        trendHistoryMapper.insert(h);
        return Result.success(Collections.singletonMap("id", h.getId()));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id, @RequestParam String browserId) {
        trendHistoryMapper.deleteById(id, browserId);
        return Result.success();
    }

    @DeleteMapping("/history/clear")
    public Result<?> clearHistory(@RequestParam String browserId) {
        trendHistoryMapper.deleteByBrowserId(browserId);
        return Result.success();
    }
}
