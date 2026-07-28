package com.orangejd.server.controller;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.controller.app.AppPlanController;
import com.orangejd.server.mapper.PlanHistoryMapper;
import com.orangejd.server.service.PlanGenerationService;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.AiRecordMetrics;
import com.orangejd.common.exception.BaseException;
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
        AppPlanController controller = new AppPlanController();
        ReflectionTestUtils.setField(controller, "planHistoryMapper", mapper);
        ReflectionTestUtils.setField(controller, "recordMetrics", mock(AiRecordMetrics.class));
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
        AppPlanController controller = new AppPlanController();
        ReflectionTestUtils.setField(controller, "planHistoryMapper", mapper);
        ReflectionTestUtils.setField(controller, "recordMetrics", mock(AiRecordMetrics.class));
        BaseContext.setCurrentId(7L);

        assertThrows(BaseException.class, () -> controller.saveHistory(
                Map.of("summary", "x".repeat(10_001))));
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void exposesCurrentPlanAndJobMatchPricing() {
        PlanGenerationService generationService = mock(PlanGenerationService.class);
        PricingService pricingService = mock(PricingService.class);
        when(generationService.cost()).thenReturn(10);
        when(pricingService.getCost("ai:match")).thenReturn(25);

        AppPlanController controller = new AppPlanController();
        ReflectionTestUtils.setField(controller, "generationService", generationService);
        ReflectionTestUtils.setField(controller, "pricingService", pricingService);

        Map<?, ?> data = (Map<?, ?>) controller.config().getData();
        assertEquals(10, data.get("stageCost"));
        assertEquals(25, data.get("jobMatchCost"));
    }
}
