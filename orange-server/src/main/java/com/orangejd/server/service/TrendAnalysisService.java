package com.orangejd.server.service;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.foundation.aitool.AiToolContext;
import com.orangejd.server.foundation.aitool.AiToolRouter;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compatibility facade retaining the existing trend-analysis API and runtime routing. */
@Service
public class TrendAnalysisService {
    private final AiRuntimeConfigService aiConfigService;
    private final AiToolRouter aiToolRouter;
    private final TrendV1Analyzer v1Analyzer;
    private final TrendV2Analyzer v2Analyzer;
    private final String trendEndpoint;
    private final String trendApiKey;
    private final String trendModel;

    public TrendAnalysisService(ObjectProvider<AiRuntimeConfigService> aiConfigService,
                                ObjectProvider<AiToolRouter> aiToolRouter,
                                TrendV1Analyzer v1Analyzer,
                                TrendV2Analyzer v2Analyzer,
                                @Value("${orange.ai-trend.endpoint:}") String trendEndpoint,
                                @Value("${orange.ai-trend.api-key:}") String trendApiKey,
                                @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}") String trendModel) {
        this.aiConfigService = aiConfigService.getIfAvailable();
        this.aiToolRouter = aiToolRouter.getIfAvailable();
        this.v1Analyzer = v1Analyzer;
        this.v2Analyzer = v2Analyzer;
        this.trendEndpoint = trendEndpoint;
        this.trendApiKey = trendApiKey;
        this.trendModel = trendModel;
    }

    public Map<String, Object> analyze(String question) {
        Long accountId = BaseContext.getCurrentId();
        String requestId = "trend_v1_" + accountId + "_" + UUID.randomUUID();
        if (aiToolRouter == null) {
            return v1Analyzer.analyze(question, accountId, requestId, projectRuntimeConfig(), false);
        }
        AiToolContext context = new AiToolContext("trend.v1.analyze", requestId,
                accountId, "TREND_V1", null, AiRuntimeConfigService.PROJECT);
        return aiToolRouter.execute(context,
                runtime -> v1Analyzer.analyze(question, accountId, requestId, runtime, true));
    }

    public Map<String, Object> analyzeV2(
            List<String> topics, String purpose, String experience, List<String> languages,
            String timeCommitment, String customTopics, String customPurpose, String customExp,
            String customLang, String customTime, Boolean useRag, Boolean usePersona,
            Boolean preferBeginnerProjects, Boolean preferCareerProjects) {
        Long accountId = BaseContext.getCurrentId();
        String requestId = "trend_v2_" + accountId + "_" + UUID.randomUUID();
        TrendAnalysisInput input = new TrendAnalysisInput(topics, purpose, experience, languages,
                timeCommitment, customTopics, customPurpose, customExp, customLang, customTime,
                useRag, usePersona, preferBeginnerProjects, preferCareerProjects);
        if (aiToolRouter == null) {
            return v2Analyzer.analyze(input, accountId, requestId, projectRuntimeConfig(), false);
        }
        AiToolContext context = new AiToolContext("trend.v2.analyze", requestId,
                accountId, "TREND_V2", null, AiRuntimeConfigService.PROJECT);
        return aiToolRouter.execute(context,
                runtime -> v2Analyzer.analyze(input, accountId, requestId, runtime, true));
    }

    private AiRuntimeConfig projectRuntimeConfig() {
        return aiConfigService == null
                ? new AiRuntimeConfig(AiRuntimeConfigService.PROJECT, "env",
                trendEndpoint, trendApiKey, trendModel, "ENV")
                : aiConfigService.current(AiRuntimeConfigService.PROJECT);
    }
}
