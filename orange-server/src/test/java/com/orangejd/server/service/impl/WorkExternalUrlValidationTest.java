package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.dto.WorkCreateDTO;
import com.orangejd.pojo.dto.WorkEditDTO;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.foundation.security.ExternalUrlPolicy;
import com.orangejd.server.mapper.AuditRecordMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.mapper.WorkSaleOptionMapper;
import com.orangejd.server.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkExternalUrlValidationTest {

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

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void createRejectsUnsafeDemoBeforeWriting() {
        BaseContext.setCurrentId(7L);
        WorkCreateDTO dto = new WorkCreateDTO();
        dto.setTitle("演示作品");
        dto.setDemoUrl("http://127.0.0.1/admin");

        assertThrows(BaseException.class, () -> service.create(dto));
        verify(workMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void editRejectsUnsafeReplacementButDoesNotInvalidateUntouchedHistory() {
        BaseContext.setCurrentId(7L);
        Work work = new Work();
        work.setId(9L);
        work.setOwnerId(7L);
        work.setDemoUrl("http://legacy.example.com");
        when(workMapper.selectById(9L)).thenReturn(work);
        WorkEditDTO dto = new WorkEditDTO();
        dto.setDemoUrl("https://192.168.1.8/admin");

        assertThrows(BaseException.class, () -> service.edit(9L, dto));
        verify(workMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }
}
