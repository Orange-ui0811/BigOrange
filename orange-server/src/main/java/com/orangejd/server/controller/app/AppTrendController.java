package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.service.KnowledgeGraphService;
import com.orangejd.server.service.TrendAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/app/trend")
public class AppTrendController {

    @Autowired
    private KnowledgeGraphService graphService;

    @Autowired
    private TrendAnalysisService analysisService;

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
}
