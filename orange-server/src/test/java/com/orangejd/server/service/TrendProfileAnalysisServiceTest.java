package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.mapper.TrendHistoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class TrendProfileAnalysisServiceTest {

    @Test
    void confirmedPersonaCanReplaceTheLegacyFormFields() {
        TrendAnalysisService trend = mock(TrendAnalysisService.class);
        TrendProfileAnalysisService service = new TrendProfileAnalysisService(
                trend, mock(TrendHistoryMapper.class), mock(PricingService.class), new ObjectMapper());
        when(trend.analyzeV2(
                eq(List.of()), eq(""), eq(""), eq(List.of()), eq(""),
                eq(""), eq(""), eq(""), eq(""), eq(""),
                eq(true), eq(true), eq(false), eq(false)))
                .thenReturn(Map.of("analysis", Map.of("summary", "ok")));

        Map<String, Object> result = service.analyze(7L, Map.of(
                "topics", List.of(), "usePersona", true));

        assertEquals(Map.of("summary", "ok"), result.get("analysis"));
        verify(trend).analyzeV2(
                eq(List.of()), eq(""), eq(""), eq(List.of()), eq(""),
                eq(""), eq(""), eq(""), eq(""), eq(""),
                eq(true), eq(true), eq(false), eq(false));
    }

    @Test
    void delegatesToV2AndSavesWebsiteHistory() {
        TrendAnalysisService trend = mock(TrendAnalysisService.class);
        TrendHistoryMapper historyMapper = mock(TrendHistoryMapper.class);
        PricingService pricing = mock(PricingService.class);
        TrendProfileAnalysisService service = new TrendProfileAnalysisService(
                trend, historyMapper, pricing, new ObjectMapper());
        Map<String, Object> analysis = Map.of(
                "recommendations", List.of(Map.of("name", "AI 工作流平台")));
        when(trend.analyzeV2(
                eq(List.of("AI智能体")), eq("学习"), eq("新手入门"),
                eq(List.of("Python")), eq("每周5小时"),
                eq(""), eq(""), eq(""), eq(""), eq(""), eq(true), eq(true), eq(true), eq(true)))
                .thenReturn(analysis);
        doAnswer(invocation -> {
            TrendHistory history = invocation.getArgument(0);
            history.setId(31L);
            return 1;
        }).when(historyMapper).insert(any(TrendHistory.class));

        Map<String, Object> result = service.analyze(7L, Map.of(
                "topics", List.of("AI智能体"),
                "purpose", "学习",
                "experience", "新手入门",
                "languages", List.of("Python"),
                "timeCommitment", "每周5小时",
                "useRag", true,
                "usePersona", true,
                "preferBeginnerProjects", true,
                "preferCareerProjects", true));

        assertEquals(analysis.get("recommendations"), result.get("recommendations"));
        assertTrue((Boolean) result.get("historySaved"));
        assertEquals(31L, result.get("historyId"));
        verify(historyMapper).insert(any(TrendHistory.class));
        verify(trend).analyzeV2(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(true), eq(true), eq(true), eq(true));
    }
}
