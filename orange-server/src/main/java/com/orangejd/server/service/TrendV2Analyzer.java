package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import com.orangejd.server.foundation.aitool.AiToolRetryPolicy;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrendV2Analyzer {
    private static final Logger log = LoggerFactory.getLogger(TrendV2Analyzer.class);
    private final KnowledgeGraphService graphService;
    private final QuotaService quotaService;
    private final PricingService pricingService;
    private final BeginnerProjectService beginnerProjects;
    private final CareerProjectService careerProjects;
    private final TrendContextBuilder contextBuilder;
    private final TrendPromptFactory promptFactory;
    private final TrendModelClient modelClient;
    private final TrendResultAssembler resultAssembler;

    public TrendV2Analyzer(KnowledgeGraphService graphService, QuotaService quotaService,
                           PricingService pricingService,
                           ObjectProvider<BeginnerProjectService> beginnerProjects,
                           ObjectProvider<CareerProjectService> careerProjects,
                           TrendContextBuilder contextBuilder, TrendPromptFactory promptFactory,
                           TrendModelClient modelClient, TrendResultAssembler resultAssembler) {
        this.graphService = graphService;
        this.quotaService = quotaService;
        this.pricingService = pricingService;
        this.beginnerProjects = beginnerProjects.getIfAvailable();
        this.careerProjects = careerProjects.getIfAvailable();
        this.contextBuilder = contextBuilder;
        this.promptFactory = promptFactory;
        this.modelClient = modelClient;
        this.resultAssembler = resultAssembler;
    }

    public Map<String, Object> analyze(TrendAnalysisInput input, Long accountId, String requestId,
                                       AiRuntimeConfig runtime, boolean routerManaged) {
        Map<String, Object> result = new LinkedHashMap<>();
        Charge charge = charge(accountId, requestId, result);
        if (result.containsKey("error")) return result;
        Map<String, Object> graph;
        try {
            graph = graphService.buildCompareGraph();
        } catch (Exception error) {
            if (charge.consumed()) quotaService.refund(accountId, charge.cost(), requestId, "图谱获取失败退款");
            result.put("error", "图谱数据获取失败");
            return result;
        }
        List<BeginnerProjectView> beginner = Boolean.TRUE.equals(input.preferBeginnerProjects())
                && beginnerProjects != null ? beginnerProjects.recommend(input.topics(), input.languages(), 5) : List.of();
        List<CareerProjectView> career = Boolean.TRUE.equals(input.preferCareerProjects())
                && careerProjects != null ? careerProjects.recommend(
                input.topics(), input.languages(), input.purpose(), 5) : List.of();
        String prompt = prompt(input, accountId, graph, beginner, career);
        try {
            String content = modelClient.complete(runtime, promptFactory.v2(runtime, prompt));
            long balance = accountId != null ? quotaService.getBalance(accountId) : 0;
            return resultAssembler.assemble(content, balance, beginner, career,
                    Boolean.TRUE.equals(input.preferBeginnerProjects())
                            || Boolean.TRUE.equals(input.preferCareerProjects()));
        } catch (Exception error) {
            handleFailure(error, accountId, requestId, charge, routerManaged, result);
            return result;
        }
    }

    private String prompt(TrendAnalysisInput input, Long accountId, Map<String, Object> graph,
                          List<BeginnerProjectView> beginner, List<CareerProjectView> career) {
        String rag = contextBuilder.rag(input);
        return contextBuilder.profile(input, accountId) + "\n\n" + contextBuilder.graphSummary(graph)
                + (rag.isEmpty() ? "" : "\n\n## 知识库参考\n" + rag)
                + "\n\n请基于用户画像、数据和知识库参考，严格按 JSON 格式输出推荐结果。";
    }

    private Charge charge(Long accountId, String requestId, Map<String, Object> result) {
        if (accountId == null || accountId <= 0) return new Charge(false, 0);
        try {
            int cost = pricingService.getCost("ai:trend:v2");
            int balance = quotaService.consume(accountId, cost, "趋势分析 V2", requestId);
            result.put("balance", balance);
            return new Charge(true, cost);
        } catch (Exception error) {
            result.put("error", error.getMessage());
            return new Charge(false, 0);
        }
    }

    private void handleFailure(Exception error, Long accountId, String requestId, Charge charge,
                               boolean routerManaged, Map<String, Object> result) {
        log.warn("AI趋势分析V2失败: {}", error.getMessage());
        boolean retryManaged = routerManaged && AiToolRetryPolicy.isRetryable(error);
        if (charge.consumed() && !retryManaged) {
            quotaService.refund(accountId, charge.cost(), requestId, "AI分析失败退款");
        }
        AiToolProviderException provider = AiToolProviderException.from(error);
        if (provider != null) throw provider;
        if (retryManaged) throw new BaseException("AI 分析暂时失败，系统将自动重试");
        result.put("error", "AI分析暂时失败，系统会自动重试");
        if (routerManaged) throw new BaseException("AI分析失败，本次配额已退回");
    }

    private record Charge(boolean consumed, int cost) {
    }
}
