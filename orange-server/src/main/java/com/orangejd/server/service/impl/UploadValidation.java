package com.orangejd.server.service.impl;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;

final class UploadValidation {
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/gif", ".gif", "image/webp", ".webp");
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", ".mp4", "video/quicktime", ".mov",
            "video/webm", ".webm", "video/x-m4v", ".m4v");
    private static final Set<String> IMAGE_DIRS = Set.of("general", "cover", "detail", "avatar");
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024;
    private static final long MAX_ATTACHMENT_SIZE = 50L * 1024 * 1024;
    private static final int MAX_DIMENSION = 12_000;

    private UploadValidation() {}

    static ValidatedUpload image(MultipartFile file, String directory, Long ownerId) {
        if (file == null || file.isEmpty()) throw new RuntimeException("文件为空");
        if (!IMAGE_DIRS.contains(directory)) throw new RuntimeException("上传目录无效");
        String type = normalizeType(file.getContentType());
        String extension = IMAGE_TYPES.get(type);
        if (extension == null) throw new RuntimeException("仅支持 png/jpeg/gif/webp 图片");
        if (file.getSize() > MAX_IMAGE_SIZE) throw new RuntimeException("图片最大允许 10MB");
        try {
            validateImageSignature(file, type);
            validateDimensions(file, type);
        } catch (IOException e) {
            throw new RuntimeException("读取图片失败", e);
        }
        requireOwner(ownerId);
        return new ValidatedUpload(directory + "/" + ownerId + "/" + UUID.randomUUID() + extension, type);
    }

    static ValidatedUpload video(MultipartFile file, Long ownerId) {
        if (file == null || file.isEmpty()) throw new RuntimeException("视频文件为空");
        if (file.getSize() > MAX_VIDEO_SIZE) throw new RuntimeException("视频最大允许 200MB");
        String type = normalizeType(file.getContentType());
        String extension = VIDEO_TYPES.get(type);
        if (extension == null) throw new RuntimeException("仅支持 MP4、MOV、M4V、WebM 视频");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new RuntimeException("视频扩展名与文件类型不一致");
        }
        try {
            validateVideoSignature(file, type);
        } catch (IOException e) {
            throw new RuntimeException("读取视频失败", e);
        }
        requireOwner(ownerId);
        return new ValidatedUpload("video/" + ownerId + "/" + UUID.randomUUID() + extension, type);
    }

    static ValidatedAttachment attachment(MultipartFile file, Long orderId, Long ownerId) {
        if (file == null || file.isEmpty()) throw new RuntimeException("附件为空");
        if (file.getSize() > MAX_ATTACHMENT_SIZE) throw new RuntimeException("附件最大允许 50MB");
        requireOwner(ownerId);
        if (orderId == null || orderId <= 0) throw new RuntimeException("订单无效");

        String originalName = sanitizeName(file.getOriginalFilename());
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        String extension = Set.of(".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".zip").stream()
                .filter(lowerName::endsWith).findFirst()
                .orElseThrow(() -> new RuntimeException("仅支持 PDF、DOCX、XLSX、PPTX、TXT、ZIP 附件"));
        String contentType = switch (extension) {
            case ".pdf" -> "application/pdf";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".txt" -> "text/plain";
            default -> "application/zip";
        };
        try {
            validateAttachmentContent(file, extension);
        } catch (IOException e) {
            throw new RuntimeException("读取附件失败", e);
        }
        String key = "delivery/" + orderId + "/" + ownerId + "/" + UUID.randomUUID() + extension;
        return new ValidatedAttachment(key, contentType, originalName, file.getSize());
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    private static void validateImageSignature(MultipartFile file, String type) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(12); }
        boolean valid = switch (type) {
            case "image/png" -> header.length >= 8 && header[0] == (byte) 0x89
                    && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47;
            case "image/jpeg" -> header.length >= 3 && header[0] == (byte) 0xff
                    && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff;
            case "image/gif" -> header.length >= 6 && header[0] == 'G'
                    && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
            case "image/webp" -> header.length >= 12 && header[0] == 'R' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                    && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!valid) throw new RuntimeException("文件内容与声明类型不一致");
    }

    private static void validateDimensions(MultipartFile file, String type) throws IOException {
        if ("image/webp".equals(type)) return;
        BufferedImage image;
        try (InputStream input = file.getInputStream()) { image = ImageIO.read(input); }
        if (image == null) throw new RuntimeException("图片内容损坏");
        if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
            throw new RuntimeException("图片尺寸超出限制");
        }
    }

    private static void validateVideoSignature(MultipartFile file, String type) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(16); }
        boolean valid = switch (type) {
            case "video/mp4", "video/quicktime", "video/x-m4v" -> header.length >= 12
                    && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            case "video/webm" -> header.length >= 4 && header[0] == 0x1a && header[1] == 0x45
                    && header[2] == (byte) 0xdf && header[3] == (byte) 0xa3;
            default -> false;
        };
        if (!valid) throw new RuntimeException("视频内容与声明格式不一致");
    }

    private static void validateAttachmentContent(MultipartFile file, String extension) throws IOException {
        if (".txt".equals(extension)) {
            byte[] bytes;
            try (InputStream input = file.getInputStream()) { bytes = input.readAllBytes(); }
            for (byte value : bytes) if (value == 0) throw new RuntimeException("TXT 附件包含二进制内容");
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes));
            } catch (java.nio.charset.CharacterCodingException e) {
                throw new RuntimeException("TXT 附件必须使用 UTF-8 编码");
            }
            return;
        }
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(8); }
        if (".pdf".equals(extension)) {
            if (header.length < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D'
                    || header[3] != 'F' || header[4] != '-') {
                throw new RuntimeException("PDF 文件内容与扩展名不一致");
            }
            return;
        }
        if (header.length < 4 || header[0] != 'P' || header[1] != 'K') {
            throw new RuntimeException("压缩或 Office 文件内容与扩展名不一致");
        }
        if (!".zip".equals(extension)) validateOfficeContainer(file, extension);
    }

    private static void validateOfficeContainer(MultipartFile file, String extension) throws IOException {
        String requiredPrefix = switch (extension) {
            case ".docx" -> "word/";
            case ".xlsx" -> "xl/";
            case ".pptx" -> "ppt/";
            default -> throw new RuntimeException("Office 文件扩展名无效");
        };
        boolean contentTypes = false;
        boolean applicationPart = false;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < 10_000) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if (name.startsWith(requiredPrefix)) applicationPart = true;
                if (name.startsWith("/") || name.contains("../") || name.contains("..\\")) {
                    throw new RuntimeException("附件包含不安全的压缩路径");
                }
            }
        }
        if (!contentTypes || !applicationPart) throw new RuntimeException("Office 文件结构无效");
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) throw new RuntimeException("附件文件名为空");
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isBlank() || value.length() > 180 || value.contains("\r") || value.contains("\n")) {
            throw new RuntimeException("附件文件名无效");
        }
        return value;
    }

    private static void requireOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new RuntimeException("无法确认上传用户");
    }

    record ValidatedUpload(String objectKey, String contentType) {}
    record ValidatedAttachment(String objectKey, String contentType, String originalName, long size) {}
}
