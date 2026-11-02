package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.foundation.security.ExternalUrlPolicy;
import com.orangejd.server.mapper.AuditRecordMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.mapper.WorkSaleOptionMapper;
import com.orangejd.server.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkWithdrawalTest {

    private final WorkMapper workMapper = mock(WorkMapper.class);
    private final WorkServiceImpl service = new WorkServiceImpl(
            workMapper,
            mock(WorkSaleOptionMapper.class),
            null,
            mock(ManagedFileStorageService.class),
            new ExternalUrlPolicy(),
            null,
            mock(AuditRecordMapper.class),
            mock(NotificationService.class));

    @BeforeEach
    void setCurrentAccount() {
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void ownerCanWithdrawPendingWorkToDraft() {
        when(workMapper.selectById(9L)).thenReturn(work(7L, "PENDING_REVIEW"));

        service.withdraw(9L);

        verify(workMapper).updateAuditStatus(9L, "DRAFT");
    }

    @Test
    void ownerCanWithdrawApprovedWorkToDraft() {
        when(workMapper.selectById(9L)).thenReturn(work(7L, "APPROVED"));

        service.withdraw(9L);

        verify(workMapper).updateAuditStatus(9L, "DRAFT");
    }

    @Test
    void draftWorkCannotBeWithdrawn() {
        when(workMapper.selectById(9L)).thenReturn(work(7L, "DRAFT"));

        assertThrows(BaseException.class, () -> service.withdraw(9L));
        verify(workMapper, never()).updateAuditStatus(9L, "DRAFT");
    }

    @Test
    void anotherAccountCannotWithdrawWork() {
        when(workMapper.selectById(9L)).thenReturn(work(8L, "APPROVED"));

        assertThrows(BaseException.class, () -> service.withdraw(9L));
        verify(workMapper, never()).updateAuditStatus(9L, "DRAFT");
    }

    private Work work(Long ownerId, String auditStatus) {
        Work work = new Work();
        work.setId(9L);
        work.setOwnerId(ownerId);
        work.setAuditStatus(auditStatus);
        return work;
    }
}
