package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.AuditRecord;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.mapper.AuditRecordMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceImplTest {

    private WorkMapper workMapper;
    private AuditRecordMapper auditRecordMapper;
    private NotificationService notificationService;
    private QuotaServiceImpl quotaService;
    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        workMapper = mock(WorkMapper.class);
        auditRecordMapper = mock(AuditRecordMapper.class);
        notificationService = mock(NotificationService.class);
        quotaService = mock(QuotaServiceImpl.class);
        service = new AuditServiceImpl();
        ReflectionTestUtils.setField(service, "workMapper", workMapper);
        ReflectionTestUtils.setField(service, "requirementMapper", mock(RequirementMapper.class));
        ReflectionTestUtils.setField(service, "auditRecordMapper", auditRecordMapper);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "quotaService", quotaService);
    }

    @Test
    void approvingWorkGrantsRewardAndMarksItInSameOperation() {
        Work work = pendingWork();
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(work);
        when(workMapper.approveAndMarkRewarded(11L)).thenReturn(1);

        service.auditWork(11L, true, "");

        verify(quotaService).grantReward(7L, 50, "作品审核通过奖励");
        verify(workMapper).approveAndMarkRewarded(11L);
        verify(auditRecordMapper).insert(any(AuditRecord.class));
        verify(notificationService).send(7L, "AUDIT", "你的作品《测试作品》审核通过", 11L);
    }

    @Test
    void rewardFailureLeavesWorkPending() {
        when(workMapper.selectByIdForUpdate(11L)).thenReturn(pendingWork());
        doThrow(new BaseException("奖励失败"))
                .when(quotaService).grantReward(7L, 50, "作品审核通过奖励");

        assertThrows(BaseException.class, () -> service.auditWork(11L, true, ""));

        verify(workMapper, never()).approveAndMarkRewarded(11L);
        verify(auditRecordMapper, never()).insert(any());
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
