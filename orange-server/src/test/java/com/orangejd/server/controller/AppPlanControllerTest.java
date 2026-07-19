package com.orangejd.server.controller;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.controller.app.AppPlanController;
import com.orangejd.server.mapper.PlanHistoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        verify(mapper).insert(any());
    }
}
