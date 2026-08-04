package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.foundation.aitool.AiResultRecorder;
import com.orangejd.server.foundation.aitool.AiToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiResultRecordService implements AiResultRecorder {
    private static final Logger log = LoggerFactory.getLogger(AiResultRecordService.class);
    private static final int MAX_RESULT_LENGTH = 8_000_000;

    private final AiResultRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;
    private final PricingService pricingService;

    public AiResultRecordService(AiResultRecordMapper mapper, ObjectMapper objectMapper,
                                 QuotaService quotaService, PricingService pricingService) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.pricingService = pricingService;
    }

    @Override
    public void saveRequired(AiToolContext context, Object result) {
        if (context.accountId() == null || context.accountId() <= 0) return;
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            if (resultJson.length() > MAX_RESULT_LENGTH) {
                throw new IllegalArgumentException("AI result is too large to persist");
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("requestId", context.requestId());
            record.put("accountId", context.accountId());
            record.put("businessType", context.businessType());
            record.put("businessId", emptyToNull(context.businessId()));
            record.put("purpose", context.purpose());
            record.put("title", title(context, result));
            record.put("summary", summary(context, result));
            record.put("resultJson", resultJson);
            mapper.upsert(record);
        } catch (Exception exception) {
            log.error("AI_RESULT_SAVE_FAILED requestId={} accountId={} type={}",
                    context.requestId(), context.accountId(), context.businessType(), exception);
            refundAfterSaveFailure(context);
            throw new BaseException("分析结果保存失败，本次额度已自动退回，请重新发起分析");
        }
    }

    public Map<String, Object> page(Long accountId, int page, int size, String businessType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeType = clean(businessType, 64);
        return Map.of(
                "records", mapper.selectPage(accountId, safeType,
                        (safePage - 1) * safeSize, safeSize),
                "total", mapper.count(accountId, safeType),
                "page", safePage,
                "size", safeSize);
    }

    public Map<String, Object> detail(Long accountId, Long id) {
        Map<String, Object> record = mapper.selectOwned(id, accountId);
        if (record == null) throw new BaseException("AI 分析记录不存在或无权访问");
        Map<String, Object> result = new LinkedHashMap<>(record);
        Object resultJson = result.remove("resultJson");
        try {
            result.put("result", objectMapper.readValue(String.valueOf(resultJson), Object.class));
            result.put("corrupted", false);
        } catch (Exception exception) {
            result.put("result", null);
            result.put("corrupted", true);
            log.error("AI_RESULT_READ_FAILED id={} accountId={}", id, accountId, exception);
        }
        return result;
    }

    public void delete(Long accountId, Long id) {
        if (mapper.deleteOwned(id, accountId) == 0) {
            throw new BaseException("AI 分析记录不存在或无权访问");
        }
    }

    private void refundAfterSaveFailure(AiToolContext context) {
        String pricingKey = pricingKey(context);
        if (pricingKey == null) return;
        try {
            int cost = pricingService.getCost(pricingKey);
            quotaService.refund(context.accountId(), cost, context.requestId(), "AI 结果保存失败退款");
        } catch (Exception refundFailure) {
            log.error("AI_RESULT_REFUND_FAILED requestId={} accountId={}",
                    context.requestId(), context.accountId(), refundFailure);
        }
    }

    private String pricingKey(AiToolContext context) {
        return switch (context.businessType()) {
            case "TREND_V1" -> "ai:trend:v1";
            case "TREND_V2" -> "ai:trend:v2";
            case "PLAN" -> "ai:plan";
            case "JOB_MATCH" -> context.businessId().startsWith("ai:")
                    ? context.businessId() : "ai:match";
            case "LAUNCH_CHECK" -> "ai:launch-check";
            case "OPEN_SOURCE_MATCH" -> "ai:open-source-match";
            case "OPEN_SOURCE_GUIDE" -> "ai:open-source-guide";
            default -> null;
        };
    }

    private String title(AiToolContext context, Object result) {
        return switch (context.businessType()) {
            case "TREND_V1" -> "趋势分析提问";
            case "TREND_V2" -> "个性化趋势分析";
            case "PLAN" -> "方案设计 · " + resultValue(result, "phaseTitle", "阶段 " + context.businessId());
            case "JOB_MATCH" -> context.businessId().contains("open-source")
                    ? "开源 Issue 岗位匹配" : "方案岗位匹配";
            case "LAUNCH_CHECK" -> "上线体检 · " + resultValue(result, "stageTitle", "阶段报告");
            case "OPEN_SOURCE_MATCH" -> "开源 Issue AI 匹配";
            case "OPEN_SOURCE_GUIDE" -> "开源贡献指南";
            default -> "AI 分析结果";
        };
    }

    private String summary(AiToolContext context, Object result) {
        String content = findContent(result);
        if (content.isBlank()) {
            if (result instanceof Map<?, ?> map && map.get("matches") instanceof List<?> matches) {
                content = "已生成 " + matches.size() + " 条匹配结果";
            } else {
                content = title(context, result);
            }
        }
        String normalized = content.replaceAll("[`#*_>\\[\\]{}]", " ")
                .replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "…";
    }

    private String findContent(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object direct = map.get("content");
            if (direct instanceof String text && !text.isBlank()) return text;
            Object guide = map.get("guide");
            if (guide != null) return findContent(guide);
            Object reply = map.get("result");
            if (reply instanceof String text && !text.isBlank()) return text;
        }
        return "";
    }

    private String resultValue(Object result, String key, String fallback) {
        if (result instanceof Map<?, ?> map && map.get(key) != null) {
            String value = String.valueOf(map.get(key)).trim();
            if (!value.isBlank()) return value.length() <= 120 ? value : value.substring(0, 120);
        }
        return fallback;
    }

    private String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
