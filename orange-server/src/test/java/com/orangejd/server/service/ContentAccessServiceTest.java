package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Requirement;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentAccessServiceTest {
    private WorkMapper workMapper;
    private RequirementMapper requirementMapper;
    private ContentAccessService service;

    @BeforeEach
    void setUp() {
        workMapper = mock(WorkMapper.class);
        requirementMapper = mock(RequirementMapper.class);
        service = new ContentAccessService(workMapper, requirementMapper);
    }

    @Test
    void publicApprovedContentIsAccessible() {
        Work work = work(10L, "APPROVED", "PUBLIC");
        when(workMapper.selectById(7L)).thenReturn(work);

        assertEquals(10L, service.requireAccessibleOwner("WORK", 7L, 20L));
    }

    @Test
    void privateContentIsOnlyAccessibleToOwner() {
        Work work = work(10L, "APPROVED", "PRIVATE");
        when(workMapper.selectById(7L)).thenReturn(work);

        assertThrows(BaseException.class, () -> service.requireAccessibleOwner("WORK", 7L, 20L));
        assertEquals(10L, service.requireAccessibleOwner("WORK", 7L, 10L));
    }

    @Test
    void rejectsUnsupportedPolymorphicTarget() {
        assertThrows(BaseException.class, () -> service.requireAccessibleOwner("ACCOUNT", 7L, 20L));
    }

    private Work work(Long ownerId, String auditStatus, String visibility) {
        Work work = new Work();
        work.setOwnerId(ownerId);
        work.setAuditStatus(auditStatus);
        work.setVisibilityStatus(visibility);
        return work;
    }
}
