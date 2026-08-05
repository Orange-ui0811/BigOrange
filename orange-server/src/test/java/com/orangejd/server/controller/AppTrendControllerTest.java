package com.orangejd.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.controller.app.AppTrendController;
import com.orangejd.server.mapper.TrendHistoryMapper;
import com.orangejd.server.service.AiRecordMetrics;
import com.orangejd.server.service.TrendHistoryService;
import com.orangejd.server.service.KnowledgeGraphService;
import com.orangejd.server.service.TrendAnalysisService;
import com.orangejd.server.service.TrendProfileAnalysisService;
import com.orangejd.common.properties.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppTrendControllerTest {

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void oneCorruptedRecordDoesNotHideValidHistory() {
        TrendHistoryMapper mapper = mock(TrendHistoryMapper.class);
        AiRecordMetrics metrics = mock(AiRecordMetrics.class);
        AppTrendController controller = controller(mapper, metrics);
        BaseContext.setCurrentId(7L);

        TrendHistory valid = history(1L, "[\"AI智能体\",\"RAG\"]", "{\"score\":9}");
        TrendHistory corrupted = history(2L, "[Java, Vue]", "{broken");
        when(mapper.selectByBrowserId(7L, 100)).thenReturn(List.of(valid, corrupted));

        List<Map<String, Object>> records = controller.history(100).getData();

        assertEquals(2, records.size());
        assertEquals(List.of("AI智能体", "RAG"), records.get(0).get("topics"));
        assertFalse((Boolean) records.get(0).get("corrupted"));
        assertEquals(List.of("Java", "Vue"), records.get(1).get("topics"));
        assertTrue((Boolean) records.get(1).get("corrupted"));
        verify(metrics).corrupted("trend");
    }

    @Test
    void rejectsInvalidManualResultBeforeDatabaseWrite() {
        TrendHistoryMapper mapper = mock(TrendHistoryMapper.class);
        AppTrendController controller = controller(mapper, mock(AiRecordMetrics.class));
        BaseContext.setCurrentId(7L);

        assertThrows(BaseException.class, () -> controller.saveHistory(Map.of(
                "topics", List.of("AI"),
                "resultString", "{broken")));

        verify(mapper, never()).insert(any());
    }

    private AppTrendController controller(TrendHistoryMapper mapper, AiRecordMetrics metrics) {
        return new AppTrendController(mock(KnowledgeGraphService.class),
                mock(TrendAnalysisService.class), mock(TrendProfileAnalysisService.class),
                new TrendHistoryService(mapper), new ObjectMapper(), metrics,
                mock(JwtProperties.class), null);
    }

    private TrendHistory history(Long id, String topics, String resultJson) {
        TrendHistory history = new TrendHistory();
        history.setId(id);
        history.setAccountId(7L);
        history.setTopics(topics);
        history.setLanguages("[]");
        history.setResultJson(resultJson);
        return history;
    }
}
