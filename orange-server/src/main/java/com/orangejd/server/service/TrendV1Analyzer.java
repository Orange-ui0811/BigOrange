package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import com.orangejd.server.foundation.aitool.AiToolRetryPolicy;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TrendV1Analyzer {
    private static final Logger log = LoggerFactory.getLogger(TrendV1Analyzer.class);
    private final KnowledgeGraphService graphService;
    private final QuotaService quotaService;
    private final PricingService pricingService;
    private final TrendContextBuilder contextBuilder;
    private final TrendPromptFactory promptFactory;
    private final TrendModelClient modelClient;

    public TrendV1Analyzer(KnowledgeGraphService graphService, QuotaService quotaService,
                           PricingService pricingService, TrendContextBuilder contextBuilder,
                           TrendPromptFactory promptFactory, TrendModelClient modelClient) {
        this.graphService = graphService;
        this.quotaService = quotaService;
        this.pricingService = pricingService;
        this.contextBuilder = contextBuilder;
        this.promptFactory = promptFactory;
        this.modelClient = modelClient;
    }

    public Map<String, Object> analyze(String question, Long accountId, String requestId,
                                       AiRuntimeConfig runtime, boolean routerManaged) {
        Map<String, Object> result = new LinkedHashMap<>();
        int cost = charge(accountId, requestId, result);
        if (result.containsKey("error")) return result;
        Map<String, Object> graph;
        try {
            graph = graphService.buildCompareGraph();
        } catch (Exception error) {
            if (cost > 0) quotaService.refund(accountId, cost, requestId, "图谱获取失败退款");
            result.put("error", "图谱数据获取失败");
            return result;
        }
        String rag = contextBuilder.simpleRag(question);
        String persona = contextBuilder.confirmedPersona(accountId);
        String prompt = contextBuilder.graphSummary(graph)
                + (rag.isEmpty() ? "" : "\n\n参考资料：\n" + rag)
                + (persona.isEmpty() ? "" : "\n\n" + persona) + "\n\n用户问题: " + question;
        try {
            result.put("content", modelClient.complete(runtime, promptFactory.v1(runtime, prompt)));
            result.put("balance", accountId != null ? quotaService.getBalance(accountId) : 0);
        } catch (Exception error) {
            handleFailure(error, accountId, requestId, cost, routerManaged, result);
        }
        return result;
    }

    private int charge(Long accountId, String requestId, Map<String, Object> result) {
        if (accountId == null || accountId <= 0) return 0;
        try {
            int cost = pricingService.getCost("ai:trend:v1");
            quotaService.consume(accountId, cost, "趋势分析 V1", requestId);
            return cost;
        } catch (Exception error) {
            result.put("error", error.getMessage());
            return 0;
        }
    }

    private void handleFailure(Exception error, Long accountId, String requestId, int cost,
                               boolean routerManaged, Map<String, Object> result) {
        log.warn("AI趋势分析V1失败: {}", error.getMessage());
        boolean retryManaged = routerManaged && AiToolRetryPolicy.isRetryable(error);
        if (cost > 0 && !retryManaged) quotaService.refund(accountId, cost, requestId, "AI分析失败退款");
        AiToolProviderException provider = AiToolProviderException.from(error);
        if (provider != null) throw provider;
        if (retryManaged) throw new BaseException("AI 分析暂时失败，系统将自动重试");
        result.put("error", "AI分析暂时失败，系统会自动重试");
        if (routerManaged) throw new BaseException("AI分析失败，本次配额已退回");
    }
}
