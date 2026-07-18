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

final class UploadValidation {
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/gif", ".gif", "image/webp", ".webp");
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", ".mp4", "video/quicktime", ".mov",
            "video/webm", ".webm", "video/x-m4v", ".m4v");
    private static final Set<String> IMAGE_DIRS = Set.of("general", "cover", "detail", "avatar");
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024;
    private static final int MAX_DIMENSION = 12_000;

    private UploadValidation() {}

    static ValidatedUpload image(MultipartFile file, String directory) {
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
        return new ValidatedUpload(directory + "/" + UUID.randomUUID() + extension, type);
    }

    static ValidatedUpload video(MultipartFile file) {
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
        return new ValidatedUpload("video/" + UUID.randomUUID() + extension, type);
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

    record ValidatedUpload(String objectKey, String contentType) {}
}
