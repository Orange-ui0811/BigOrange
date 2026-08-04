package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.AuditRecord;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.mapper.AuditRecordMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.service.NotificationService;
import com.orangejd.server.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditServiceImplTest {

    private WorkMapper workMapper;
    private AuditRecordMapper auditRecordMapper;
    private NotificationService notificationService;
    private QuotaServiceImpl quotaService;
    private PricingService pricingService;
    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        workMapper = mock(WorkMapper.class);
        auditRecordMapper = mock(AuditRecordMapper.class);
        notificationService = mock(NotificationService.class);
        quotaService = mock(QuotaServiceImpl.class);
        pricingService = mock(PricingService.class);
        when(pricingService.getReward(PricingService.REWARD_WORK_APPROVED)).thenReturn(65);
        service = new AuditServiceImpl(
                workMapper,
                mock(RequirementMapper.class),
                auditRecordMapper,
                notificationService,
                quotaService,
                pricingService
        );
    }

    @Test
    void approvingWorkGrantsRewardAndMarksItInSameOperation() {
        Work work = pendingWork();
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(work);
        when(workMapper.approveAndMarkRewarded(11L)).thenReturn(1);

        service.auditWork(11L, true, "");

        verify(quotaService).grantReward(7L, 65, "作品审核通过奖励");
        verify(workMapper).approveAndMarkRewarded(11L);
        verify(auditRecordMapper).insert(any(AuditRecord.class));
        verify(notificationService).send(7L, "AUDIT", "你的作品《测试作品》审核通过", 11L);
    }

    @Test
    void rewardFailureLeavesWorkPending() {
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(pendingWork());
        doThrow(new BaseException("奖励失败"))
                .when(quotaService).grantReward(7L, 65, "作品审核通过奖励");

        assertThrows(BaseException.class, () -> service.auditWork(11L, true, ""));

        verify(workMapper, never()).approveAndMarkRewarded(11L);
        verify(auditRecordMapper, never()).insert(any());
    }

    @Test
    void zeroRewardStillApprovesWithoutCallingQuotaService() {
        when(pricingService.getReward(PricingService.REWARD_WORK_APPROVED)).thenReturn(0);
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(pendingWork());
        when(workMapper.approveAndMarkRewarded(11L)).thenReturn(1);

        service.auditWork(11L, true, "");

        verify(quotaService, never()).grantReward(any(), any(Integer.class), any());
        verify(workMapper).approveAndMarkRewarded(11L);
    }

    @Test
    void approvingPreviouslyRewardedWorkDoesNotGrantRewardAgain() {
        Work work = pendingWork();
        work.setApprovalRewardGranted(true);
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(work);
        when(workMapper.approvePreviouslyRewarded(11L)).thenReturn(1);

        service.auditWork(11L, true, "再次审核通过");

        verify(workMapper).approvePreviouslyRewarded(11L);
        verify(workMapper, never()).approveAndMarkRewarded(11L);
        verifyNoInteractions(quotaService);
        verify(auditRecordMapper).insert(any(AuditRecord.class));
        verify(notificationService).send(7L, "AUDIT", "你的作品《测试作品》审核通过", 11L);
    }

    @Test
    void previouslyRewardedApprovalConflictDoesNotWriteAuditRecord() {
        Work work = pendingWork();
        work.setApprovalRewardGranted(true);
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(work);
        when(workMapper.approvePreviouslyRewarded(11L)).thenReturn(0);

        assertThrows(BaseException.class, () -> service.auditWork(11L, true, ""));

        verifyNoInteractions(quotaService);
        verify(auditRecordMapper, never()).insert(any());
        verify(notificationService, never()).send(any(), any(), any(), any());
    }

    private Work pendingWork() {
        Work work = new Work();
        work.setId(11L);
        work.setOwnerId(7L);
        work.setTitle("测试作品");
        work.setAuditStatus("PENDING_REVIEW");
        work.setApprovalRewardGranted(false);
        return work;
    }
}
