package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.foundation.aitool.AiToolContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiResultRecordServiceTest {

    @Test
    void persistsACompleteResultWithAReadableTitleAndSummary() {
        AiResultRecordMapper mapper = mock(AiResultRecordMapper.class);
        AiResultRecordService service = service(mapper, mock(QuotaService.class),
                mock(PricingService.class));
        AiToolContext context = new AiToolContext("plan.phase.generate", "request-1", 7L,
                "PLAN", "2", "project");

        service.saveRequired(context, Map.of(
                "phaseTitle", "成功标准",
                "content", "## 可验证目标\n完成登录与额度校验"));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mapper).upsert(captor.capture());
        assertEquals("方案设计 · 成功标准", captor.getValue().get("title"));
        assertEquals("可验证目标 完成登录与额度校验", captor.getValue().get("summary"));
    }

    @Test
    void refundsQuotaWhenACompletedResultCannotBeStored() {
        AiResultRecordMapper mapper = mock(AiResultRecordMapper.class);
        QuotaService quotaService = mock(QuotaService.class);
        PricingService pricingService = mock(PricingService.class);
        AiResultRecordService service = service(mapper, quotaService, pricingService);
        AiToolContext context = new AiToolContext("trend.v2.analyze", "request-2", 8L,
                "TREND_V2", null, "project");
        doThrow(new IllegalStateException("database unavailable")).when(mapper).upsert(any());

        assertThrows(BaseException.class,
                () -> service.saveRequired(context, Map.of("content", "result")));

        verify(quotaService).refund(eq(8L), eq("request-2"),
                startsWith("AI 结果保存失败"));
    }

    private AiResultRecordService service(AiResultRecordMapper mapper,
                                          QuotaService quotaService,
                                          PricingService pricingService) {
        return new AiResultRecordService(mapper, new ObjectMapper(), quotaService, pricingService);
    }
}
