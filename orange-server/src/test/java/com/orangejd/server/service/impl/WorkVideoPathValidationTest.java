package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkVideoPathValidationTest {

    @Test
    void acceptsOnlyUploadedVideoPath() {
        WorkServiceImpl service = new WorkServiceImpl(
                org.mockito.Mockito.mock(com.orangejd.server.mapper.WorkMapper.class),
                org.mockito.Mockito.mock(com.orangejd.server.mapper.WorkSaleOptionMapper.class),
                null,
                org.mockito.Mockito.mock(ManagedFileStorageService.class),
                null,
                org.mockito.Mockito.mock(com.orangejd.server.mapper.AuditRecordMapper.class),
                org.mockito.Mockito.mock(com.orangejd.server.service.NotificationService.class));
        String path = "/uploads/video/123e4567-e89b-12d3-a456-426614174000.mp4";
        String ossPath = "/api/media/video/123e4567-e89b-12d3-a456-426614174000.mp4";
        String ownedOssPath = "/api/media/video/42/123e4567-e89b-12d3-a456-426614174000.mp4";

        assertEquals(path, ReflectionTestUtils.invokeMethod(service, "normalizeVideoUrl", path));
        assertEquals(ossPath, ReflectionTestUtils.invokeMethod(service, "normalizeVideoUrl", ossPath));
        assertEquals(ownedOssPath, ReflectionTestUtils.invokeMethod(service, "normalizeVideoUrl", ownedOssPath));
        assertThrows(BaseException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "normalizeVideoUrl", "https://example.com/demo.mp4"));
        assertThrows(BaseException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "normalizeVideoUrl", "/uploads/cover/demo.mp4"));
    }
}
