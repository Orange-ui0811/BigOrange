package com.orangejd.server.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryAttachmentValidationTest {
    @Test
    void acceptsPdfAndBuildsOrderScopedKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "方案.pdf", "application/pdf", "%PDF-1.7\nbody".getBytes());

        var result = UploadValidation.attachment(file, 12L, 42L);

        assertTrue(result.objectKey().matches("^delivery/12/42/[0-9a-f-]{36}\\.pdf$"));
        assertEquals("方案.pdf", result.originalName());
    }

    @Test
    void acceptsValidDocxContainer() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "work.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());

        assertTrue(UploadValidation.attachment(file, 12L, 42L).objectKey().endsWith(".docx"));
    }

    @Test
    void rejectsExecutableAndFakeOfficeFile() {
        assertThrows(RuntimeException.class, () -> UploadValidation.attachment(
                new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {1, 2}), 12L, 42L));
        assertThrows(RuntimeException.class, () -> UploadValidation.attachment(
                new MockMultipartFile("file", "fake.docx", "application/octet-stream", "fake".getBytes()), 12L, 42L));
    }

    @Test
    void enforcesDeliveryObjectOwnership() {
        String key = "delivery/12/42/123e4567-e89b-12d3-a456-426614174000.pdf";
        ManagedFileStorageService.requireDeliveryOwner(key, 12L, 42L);
        assertThrows(IllegalArgumentException.class,
                () -> ManagedFileStorageService.requireDeliveryOwner(key, 12L, 99L));
    }
}
