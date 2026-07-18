package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileUploadServiceImplTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir Path tempDir;

    @BeforeEach void setUser() { BaseContext.setCurrentId(42L); }
    @AfterEach void clearUser() { BaseContext.clear(); }

    @Test
    void acceptsRealImageInKnownDirectory() {
        LocalFileUploadServiceImpl service = service();
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", PNG);
        String url = service.upload(file, "avatar");
        assertTrue(url.startsWith("/uploads/avatar/42/"));
    }

    @Test
    void rejectsTraversalAndFakeContentType() {
        LocalFileUploadServiceImpl service = service();
        MockMultipartFile image = new MockMultipartFile("file", "x.png", "image/png", PNG);
        assertThrows(RuntimeException.class, () -> service.upload(image, "../outside"));

        MockMultipartFile fake = new MockMultipartFile("file", "x.png", "image/png", "not-an-image".getBytes());
        assertThrows(RuntimeException.class, () -> service.upload(fake, "general"));
    }

    @Test
    void acceptsSupportedVideoFiles() throws Exception {
        LocalFileUploadServiceImpl service = service();

        String mp4Url = service.uploadVideo(new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", isoBaseMediaHeader()));
        String movUrl = service.uploadVideo(new MockMultipartFile(
                "file", "demo.mov", "video/quicktime", isoBaseMediaHeader()));
        String webmUrl = service.uploadVideo(new MockMultipartFile(
                "file", "demo.webm", "video/webm",
                new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0x01, 0x02}));

        assertTrue(mp4Url.matches("^/uploads/video/42/[0-9a-f-]{36}\\.mp4$"));
        assertTrue(movUrl.endsWith(".mov"));
        assertTrue(webmUrl.endsWith(".webm"));
        assertEquals(3, Files.list(tempDir.resolve("video/42")).count());
    }

    @Test
    void rejectsFakeVideoAndMismatchedExtension() {
        LocalFileUploadServiceImpl service = service();
        MockMultipartFile fake = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", "not-a-video".getBytes());
        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "demo.mov", "video/mp4", isoBaseMediaHeader());

        assertThrows(RuntimeException.class, () -> service.uploadVideo(fake));
        assertThrows(RuntimeException.class, () -> service.uploadVideo(mismatched));
    }

    private byte[] isoBaseMediaHeader() {
        return new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0x00, 0x00, 0x00, 0x00};
    }

    private LocalFileUploadServiceImpl service() {
        LocalFileUploadServiceImpl service = new LocalFileUploadServiceImpl();
        ReflectionTestUtils.setField(service, "basePath", tempDir.toString());
        return service;
    }
}
