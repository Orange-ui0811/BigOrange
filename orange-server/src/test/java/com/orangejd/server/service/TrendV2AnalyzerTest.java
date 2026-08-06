package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendV2AnalyzerTest {

    @SuppressWarnings("unchecked")
    @Test
    void preferredProjectSourcesAreCappedAtThree() {
        KnowledgeGraphService graph = mock(KnowledgeGraphService.class);
        BeginnerProjectService beginner = mock(BeginnerProjectService.class);
        CareerProjectService career = mock(CareerProjectService.class);
        ObjectProvider<BeginnerProjectService> beginnerProvider = mock(ObjectProvider.class);
        ObjectProvider<CareerProjectService> careerProvider = mock(ObjectProvider.class);
        TrendContextBuilder context = mock(TrendContextBuilder.class);
        TrendPromptFactory prompts = mock(TrendPromptFactory.class);
        TrendModelClient model = mock(TrendModelClient.class);
        TrendResultAssembler assembler = mock(TrendResultAssembler.class);
        AiRuntimeConfig runtime = new AiRuntimeConfig("project", "v1", "https://ai.example/v1", "key", "model", "TEST");
        TrendAnalysisInput input = new TrendAnalysisInput(List.of("AI智能体"), "求职", "新手入门",
                List.of("TypeScript"), "每周5-15小时", "", "", "", "", "",
                true, true, true, true);
        BeginnerProjectView beginnerView = mock(BeginnerProjectView.class);
        CareerProjectView careerView = mock(CareerProjectView.class);

        when(beginnerProvider.getIfAvailable()).thenReturn(beginner);
        when(careerProvider.getIfAvailable()).thenReturn(career);
        when(graph.buildCompareGraph()).thenReturn(Map.of());
        when(beginner.recommend(input.topics(), input.languages(), 3)).thenReturn(List.of(beginnerView));
        when(career.recommend(input.topics(), input.languages(), input.purpose(), 3)).thenReturn(List.of(careerView));
        when(context.profile(input, null)).thenReturn("profile");
        when(context.graphSummary(any())).thenReturn("graph");
        when(context.rag(input)).thenReturn("");
        when(context.beginnerCases(any())).thenReturn("beginner");
        when(context.careerCases(any())).thenReturn("career");
        when(prompts.v2(eq(runtime), any())).thenReturn(Map.of());
        when(model.complete(runtime, Map.of())).thenReturn("result");
        when(assembler.assemble(eq("result"), eq(0L), any(), any(), eq(true), eq(input)))
                .thenReturn(Map.of("ok", true));

        TrendV2Analyzer analyzer = new TrendV2Analyzer(graph, mock(QuotaService.class),
                mock(PricingService.class), beginnerProvider, careerProvider, context, prompts, model, assembler);

        assertEquals(true, analyzer.analyze(input, null, "request", runtime, false).get("ok"));
        verify(beginner).recommend(input.topics(), input.languages(), 3);
        verify(career).recommend(input.topics(), input.languages(), input.purpose(), 3);
    }
}
