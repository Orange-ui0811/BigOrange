package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.OpenSourceMapper;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import com.orangejd.server.task.OpenSourceRefreshTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenSourceContributionServiceTest {

    private OpenSourceMapper mapper;
    private OpenSourceAiClient aiClient;
    private PricingService pricingService;
    private QuotaServiceImpl quotaService;
    private OpenSourceContributionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OpenSourceMapper.class);
        aiClient = mock(OpenSourceAiClient.class);
        pricingService = mock(PricingService.class);
        quotaService = mock(QuotaServiceImpl.class);
        when(pricingService.getCost(OpenSourceContributionService.MATCH_PRICING_KEY)).thenReturn(40);
        when(quotaService.consume(eq(7L), eq(40), anyString(), anyString())).thenReturn(960);
        service = new OpenSourceContributionService(mapper, mock(OpenSourceGithubService.class),
                mock(OpenSourceTranslationService.class), mock(OpenSourceRefreshTask.class), aiClient,
                mock(JobMatchService.class), pricingService, quotaService, new ObjectMapper());
    }

    @Test
    void selectedCandidatesConstrainAiMatchingPool() {
        List<Long> selectedIds = List.of(11L, 12L, 13L);
        List<Map<String, Object>> candidates = selectedIds.stream().map(this::issue).toList();
        when(mapper.selectIssuesByIds(selectedIds)).thenReturn(candidates);
        candidates.forEach(candidate -> when(mapper.selectIssueById((Long) candidate.get("id")))
                .thenReturn(new LinkedHashMap<>(candidate)));
        when(aiClient.call(anyString(), anyString(), eq(0.2))).thenReturn(
                "{\"recommendedIssueId\":11,\"matches\":["
                        + "{\"issueId\":11,\"reason\":\"匹配\",\"risk\":\"低\",\"difficulty\":\"入门\"},"
                        + "{\"issueId\":12,\"reason\":\"匹配\",\"risk\":\"低\",\"difficulty\":\"简单\"},"
                        + "{\"issueId\":13,\"reason\":\"匹配\",\"risk\":\"低\",\"difficulty\":\"简单\"}]}"
        );

        Map<String, Object> result = service.match(7L, Map.of("selectedIssueIds", selectedIds));

        assertEquals(3, ((List<?>) result.get("matches")).size());
        assertEquals(11L, result.get("recommendedIssueId"));
        verify(mapper).selectIssuesByIds(selectedIds);
        verify(mapper, never()).selectPublicIssues(anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void malformedAiResponseRefundsQuota() {
        List<Map<String, Object>> candidates = List.of(issue(11L), issue(12L), issue(13L));
        when(mapper.selectPublicIssues(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(candidates);
        when(aiClient.call(anyString(), anyString(), eq(0.2))).thenReturn("not-json");

        assertThrows(BaseException.class, () -> service.match(7L, Map.of()));

        verify(quotaService).refund(eq(7L), eq(40), startsWith("opensource_match_7_"),
                eq("开源 Issue 匹配失败退款"));
    }

    private Map<String, Object> issue(Long id) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("id", id);
        issue.put("titleOriginal", "Issue " + id);
        issue.put("titleZh", "问题 " + id);
        issue.put("summaryZh", "摘要");
        issue.put("techStack", "TypeScript");
        issue.put("difficulty", "EASY");
        issue.put("participationStatus", "TRY");
        issue.put("commentsCount", 0);
        issue.put("labelsJson", "[]");
        issue.put("assigneesJson", "[]");
        issue.put("riskFlagsJson", "[]");
        return issue;
    }
}
