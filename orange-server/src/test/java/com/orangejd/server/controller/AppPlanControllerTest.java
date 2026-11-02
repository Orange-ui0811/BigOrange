package com.orangejd.server.controller;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.controller.app.AppPlanController;
import com.orangejd.server.mapper.PlanHistoryMapper;
import com.orangejd.server.service.PlanGenerationService;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.AiRecordMetrics;
import com.orangejd.server.service.PlanHistoryService;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.properties.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppPlanControllerTest {

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void savesTaskPlanAndJobMatchInHistory() {
        PlanHistoryMapper mapper = mock(PlanHistoryMapper.class);
        AppPlanController controller = controller(mapper, null, null, mock(AiRecordMetrics.class), null);
        BaseContext.setCurrentId(7L);

        Map<String, Object> captured = new LinkedHashMap<>();
        doAnswer(invocation -> {
            Map<String, Object> record = invocation.getArgument(0);
            captured.putAll(record);
            record.put("id", 15L);
            return 1;
        }).when(mapper).insert(any());

        controller.saveHistory(Map.of(
                "summary", "测试项目",
                "phase1", "想法",
                "phase2", "需求",
                "phase3", "标准",
                "phase4", "风险",
                "phase5", "任务计划",
                "phase6", "岗位匹配"
        ));

        assertEquals("任务计划", captured.get("phase5"));
        assertEquals("岗位匹配", captured.get("phase6"));
        assertEquals(64, String.valueOf(captured.get("contentHash")).length());
        verify(mapper).insert(any());
    }

    @Test
    void rejectsOversizedSummaryBeforeDatabaseWrite() {
        PlanHistoryMapper mapper = mock(PlanHistoryMapper.class);
        AppPlanController controller = controller(mapper, null, null, mock(AiRecordMetrics.class), null);
        BaseContext.setCurrentId(7L);

        assertThrows(BaseException.class, () -> controller.saveHistory(
                Map.of("summary", "x".repeat(10_001))));
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void updatesExistingDraftInsteadOfCreatingAnotherHistoryRecord() {
        PlanHistoryMapper mapper = mock(PlanHistoryMapper.class);
        AppPlanController controller = controller(mapper, null, null, mock(AiRecordMetrics.class), null);
        BaseContext.setCurrentId(7L);
        when(mapper.updateOwned(any())).thenReturn(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("historyId", 21L);
        body.put("summary", "测试项目");
        body.put("phase1", "想法描述");
        body.put("phase2", "需求理解");

        Map<?, ?> data = (Map<?, ?>) controller.saveHistory(body).getData();

        assertEquals(21L, data.get("id"));
        verify(mapper).updateOwned(any());
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void generatedPhaseIsPersistedBeforeTheResponseReturns() {
        PlanHistoryMapper mapper = mock(PlanHistoryMapper.class);
        PlanGenerationService generationService = mock(PlanGenerationService.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppPlanController controller = controller(mapper, generationService, null,
                mock(AiRecordMetrics.class), jwtProperties);
        BaseContext.setCurrentId(7L);
        when(jwtProperties.getAppTokenName()).thenReturn("App-Token");
        when(request.getHeader("App-Token")).thenReturn(null);
        when(generationService.generate(any(), any())).thenReturn(new LinkedHashMap<>(Map.of(
                "phase", 1,
                "content", "需求理解结果"
        )));
        doAnswer(invocation -> {
            Map<String, Object> record = invocation.getArgument(0);
            record.put("id", 33L);
            return 1;
        }).when(mapper).insert(any());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", "测试项目");
        body.put("phase1", "想法描述");
        Map<?, ?> data = (Map<?, ?>) controller.generate(body, request).getData();

        assertEquals(33L, data.get("historyId"));
        assertEquals("需求理解结果", data.get("content"));
        verify(mapper).insert(any());
    }

    @Test
    void exposesCurrentPlanAndJobMatchPricing() {
        PlanGenerationService generationService = mock(PlanGenerationService.class);
        PricingService pricingService = mock(PricingService.class);
        when(generationService.cost()).thenReturn(10);
        when(pricingService.getCost("ai:match")).thenReturn(25);

        AppPlanController controller = controller(mock(PlanHistoryMapper.class), generationService,
                pricingService, mock(AiRecordMetrics.class), mock(JwtProperties.class));

        Map<?, ?> data = (Map<?, ?>) controller.config().getData();
        assertEquals(10, data.get("stageCost"));
        assertEquals(25, data.get("jobMatchCost"));
    }

    private AppPlanController controller(PlanHistoryMapper mapper,
                                         PlanGenerationService generationService,
                                         PricingService pricingService,
                                         AiRecordMetrics metrics,
                                         JwtProperties jwtProperties) {
        return new AppPlanController(new PlanHistoryService(mapper),
                jwtProperties != null ? jwtProperties : mock(JwtProperties.class),
                generationService != null ? generationService : mock(PlanGenerationService.class),
                pricingService != null ? pricingService : mock(PricingService.class), metrics);
    }
}
