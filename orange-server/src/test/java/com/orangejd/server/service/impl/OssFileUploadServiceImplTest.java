package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OssFileUploadServiceImplTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @BeforeEach void setUser() { BaseContext.setCurrentId(42L); }
    @AfterEach void clearUser() { BaseContext.clear(); }

    @Test
    void uploadsValidatedImageAndReturnsStableMediaPath() {
        OssObjectStorage storage = mock(OssObjectStorage.class);
        OssFileUploadServiceImpl service = new OssFileUploadServiceImpl(storage);
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG);

        String url = service.upload(file, "cover");

        assertTrue(url.matches("^/api/media/cover/42/[0-9a-f-]{36}\\.png$"));
        String objectKey = url.substring("/api/media/".length());
        verify(storage).put(eq(objectKey), eq("image/png"), eq((long) PNG.length), any(InputStream.class));
    }

    @Test
    void rejectsInvalidDirectoryBeforeCallingOss() {
        OssFileUploadServiceImpl service = new OssFileUploadServiceImpl(mock(OssObjectStorage.class));
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG);
        assertThrows(RuntimeException.class, () -> service.upload(file, "../outside"));
    }
}
