package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.LaunchAssessmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaunchCheckServiceTest {

    private LaunchAssessmentMapper mapper;
    private QuotaService quotaService;
    private PricingService pricingService;
    private LaunchAssessmentReportService reportService;
    private LaunchCheckService service;

    @BeforeEach
    void setUp() {
        mapper = mock(LaunchAssessmentMapper.class);
        quotaService = mock(QuotaService.class);
        pricingService = mock(PricingService.class);
        reportService = mock(LaunchAssessmentReportService.class);
        when(pricingService.getCost(LaunchCheckService.PRICING_KEY)).thenReturn(40);
        service = new LaunchCheckService(mapper, quotaService, pricingService, reportService);
    }

    @Test
    void exposesEightStagesAndConfiguredCost() {
        Map<String, Object> config = service.config();

        assertEquals(40, config.get("stageCost"));
        assertEquals(8, ((List<?>) config.get("stages")).size());
    }

    @Test
    void rejectsUnsupportedRepositoryHost() {
        Map<String, Object> body = validProject();
        body.put("repositoryUrl", "https://example.com/owner/repo");

        assertThrows(BaseException.class, () -> service.create(7L, body));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createsProjectWithoutChargingQuota() {
        Map<String, Object> owned = new LinkedHashMap<>(validProject());
        owned.put("id", 12L);
        owned.put("accountId", 7L);
        owned.put("currentStage", 0);
        owned.put("status", "IN_PROGRESS");
        doAnswer(invocation -> {
            Map<String, Object> record = invocation.getArgument(0);
            record.put("id", 12L);
            return 1;
        }).when(mapper).insert(any());
        when(mapper.selectOwned(12L, 7L)).thenReturn(owned);
        when(mapper.selectStages(12L)).thenReturn(List.of());

        Map<String, Object> created = service.create(7L, validProject());

        assertEquals(12L, created.get("id"));
        verify(quotaService, never()).consume(any(), anyInt(), any(), any());
    }

    @Test
    void cannotSkipPreviousStage() {
        Map<String, Object> owned = new LinkedHashMap<>(validProject());
        owned.put("id", 12L);
        when(mapper.selectOwned(12L, 7L)).thenReturn(owned);
        when(mapper.selectStage(12L, 1)).thenReturn(null);

        assertThrows(BaseException.class, () -> service.generate(7L, 12L, 2));
        verify(quotaService, never()).consume(any(), anyInt(), any(), any());
    }

    private Map<String, Object> validProject() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectName", "OrangeVC");
        body.put("repositoryUrl", "https://github.com/example/orangevc");
        body.put("projectSummary", "这是一个包含前端、后端、数据库、Redis 和对象存储的完整测试项目。");
        body.put("targetAudience", "个人开发者");
        body.put("paymentRequired", false);
        return body;
    }
}
