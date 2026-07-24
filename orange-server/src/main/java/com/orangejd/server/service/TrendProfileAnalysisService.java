package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.mapper.TrendHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrendProfileAnalysisService {
    public static final String PRICING_KEY = "ai:trend:v2";
    private static final Logger log = LoggerFactory.getLogger(TrendProfileAnalysisService.class);

    private final TrendAnalysisService trendAnalysisService;
    private final TrendHistoryMapper trendHistoryMapper;
    private final PricingService pricingService;
    private final ObjectMapper objectMapper;

    public TrendProfileAnalysisService(TrendAnalysisService trendAnalysisService,
                                       TrendHistoryMapper trendHistoryMapper,
                                       PricingService pricingService,
                                       ObjectMapper objectMapper) {
        this.trendAnalysisService = trendAnalysisService;
        this.trendHistoryMapper = trendHistoryMapper;
        this.pricingService = pricingService;
        this.objectMapper = objectMapper;
    }

    public int cost() {
        return pricingService.getCost(PRICING_KEY);
    }

    public Map<String, Object> analyze(Long accountId, Map<String, Object> parameters) {
        List<String> topics = strings(parameters.get("topics"));
        if (topics.isEmpty()) throw new BaseException("请至少说明一个感兴趣的技术方向");
        List<String> languages = strings(parameters.get("languages"));
        String purpose = text(parameters.get("purpose"), 200);
        String experience = text(parameters.get("experience"), 200);
        String timeCommitment = text(parameters.get("timeCommitment"), 200);
        if (!StringUtils.hasText(purpose) || !StringUtils.hasText(experience)
                || !StringUtils.hasText(timeCommitment)) {
            throw new BaseException("趋势分析画像信息不完整");
        }

        Map<String, Object> result = trendAnalysisService.analyzeV2(
                topics, purpose, experience, languages, timeCommitment,
                text(parameters.get("customTopics"), 200),
                text(parameters.get("customPurpose"), 200),
                text(parameters.get("customExp"), 200),
                text(parameters.get("customLang"), 200),
                text(parameters.get("customTime"), 200),
                !Boolean.FALSE.equals(parameters.get("useRag")));
        if (result.containsKey("error")) {
            throw new BaseException(String.valueOf(result.get("error")));
        }
        saveHistory(accountId, topics, purpose, experience, languages, timeCommitment, result);
        return result;
    }

    private void saveHistory(Long accountId, List<String> topics, String purpose,
                             String experience, List<String> languages,
                             String timeCommitment, Map<String, Object> result) {
        if (accountId == null || accountId <= 0) return;
        try {
            TrendHistory history = new TrendHistory();
            history.setAccountId(accountId);
            history.setTopics(String.join(",", topics));
            history.setPurpose(purpose);
            history.setExperience(experience);
            history.setLanguages(String.join(",", languages));
            history.setTimeCommitment(timeCommitment);
            history.setResultJson(objectMapper.writeValueAsString(result));
            trendHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("设备趋势分析已生成，但保存历史记录失败: accountId={}", accountId, e);
        }
    }

    private List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = text(item, 100);
                if (StringUtils.hasText(text)) result.add(text);
            }
            return result;
        }
        String text = text(value, 500);
        if (!StringUtils.hasText(text)) return List.of();
        return List.of(text.split("[,，、/]")).stream()
                .map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String text(Object value, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > maxLength) throw new BaseException("趋势分析画像内容过长");
        return result;
    }
}
