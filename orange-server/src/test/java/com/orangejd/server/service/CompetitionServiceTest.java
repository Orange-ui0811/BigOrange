package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.AiCompetition;
import com.orangejd.server.dto.CompetitionRequest;
import com.orangejd.server.mapper.AiCompetitionMapper;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionServiceTest {

    private AiCompetitionMapper mapper;
    private QuotaServiceImpl quotaService;
    private PricingService pricingService;
    private CompetitionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiCompetitionMapper.class);
        quotaService = mock(QuotaServiceImpl.class);
        pricingService = mock(PricingService.class);
        when(pricingService.getReward(PricingService.REWARD_COMPETITION_APPROVED)).thenReturn(35);
        service = new CompetitionService(mapper, quotaService, pricingService);
    }

    @Test
    void normalizesHttpsUrlAndRejectsUnsafeSchemes() {
        assertEquals("https://example.com/path?a=1",
                CompetitionService.normalizeUrl("HTTPS://Example.COM:443/path/?a=1#fragment"));
        assertThrows(BaseException.class,
                () -> CompetitionService.normalizeUrl("http://example.com/event"));
        assertThrows(BaseException.class,
                () -> CompetitionService.normalizeUrl("https://user:secret@example.com/event"));
    }

    @Test
    void rejectsInvalidTimeRangeBeforeInsert() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 1, 9, 0);
        CompetitionRequest request = new CompetitionRequest(
                "AI 大赛", "https://example.com/event", "比赛简介", "举办方", time, time);

        assertThrows(BaseException.class, () -> service.submit(request, 7L, "127.0.0.1"));
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsCompetitionWithoutPublishedDates() {
        CompetitionRequest request = new CompetitionRequest(
                "AI 大赛", "https://example.com/event", "比赛简介", "举办方", null, null);

        service.submit(request, 7L, "127.0.0.1");

        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(value ->
                value.getStartTime() == null && value.getEndTime() == null));
    }

    @Test
    void approvalRewardsUserExactlyOnce() {
        AiCompetition pending = pendingCompetition();
        AiCompetition approved = pendingCompetition();
        approved.setAuditStatus("APPROVED");
        approved.setRewardGranted(true);
        when(mapper.selectByIdForUpdate(12L)).thenReturn(pending, approved);
        when(mapper.approve(eq(12L), eq(3L), eq(true), eq(35))).thenReturn(1);

        service.approve(12L, 3L);
        assertThrows(BaseException.class, () -> service.approve(12L, 3L));

        verify(quotaService).grantReward(7L, PricingService.REWARD_COMPETITION_APPROVED,
                "competition:12", "AI大赛投稿审核通过");
        verify(mapper).approve(12L, 3L, true, 35);
    }

    @Test
    void rewardFailurePreventsApprovalUpdate() {
        when(mapper.selectByIdForUpdate(12L)).thenReturn(pendingCompetition());
        doThrow(new BaseException("奖励失败"))
                .when(quotaService).grantReward(7L,
                        PricingService.REWARD_COMPETITION_APPROVED,
                        "competition:12", "AI大赛投稿审核通过");

        assertThrows(BaseException.class, () -> service.approve(12L, 3L));

        verify(mapper, never()).approve(eq(12L), eq(3L), anyBoolean(), anyInt());
    }

    @Test
    void adminCreatedCompetitionDoesNotReceiveReward() {
        AiCompetition pending = pendingCompetition();
        pending.setSubmissionSource("ADMIN");
        pending.setSubmitterId(null);
        when(mapper.selectByIdForUpdate(12L)).thenReturn(pending);
        when(mapper.approve(12L, 3L, false, 0)).thenReturn(1);

        service.approve(12L, 3L);

        verify(quotaService, never()).grantReward(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(mapper).approve(12L, 3L, false, 0);
    }

    private AiCompetition pendingCompetition() {
        AiCompetition competition = new AiCompetition();
        competition.setId(12L);
        competition.setAuditStatus("PENDING");
        competition.setSubmissionSource("USER");
        competition.setSubmitterId(7L);
        competition.setRewardGranted(false);
        return competition;
    }
}
