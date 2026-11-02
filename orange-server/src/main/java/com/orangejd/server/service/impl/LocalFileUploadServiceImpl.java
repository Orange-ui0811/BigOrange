package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
@ConditionalOnProperty(name = "orange.oss.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileUploadServiceImpl implements FileUploadService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/gif", ".gif", "image/webp", ".webp");
    private static final Set<String> ALLOWED_DIRS = Set.of("general", "cover", "detail", "avatar");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Map<String, String> ALLOWED_VIDEO_TYPES = Map.of(
            "video/mp4", ".mp4",
            "video/quicktime", ".mov",
            "video/webm", ".webm",
            "video/x-m4v", ".m4v");
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024; // 200MB
    private static final String VIDEO_DIR = "video";
    private static final int MAX_DIMENSION = 12_000;

    @Value("${orange.upload.path}")
    private String basePath;

    @Override
    public String upload(MultipartFile file, String subDir) {
        UploadValidation.ValidatedUpload validated =
                UploadValidation.image(file, subDir, BaseContext.getCurrentId());
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(validated.objectKey()).normalize();
        if (!target.startsWith(base)) throw new RuntimeException("上传目录无效");
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return "/uploads/" + validated.objectKey();
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public String uploadVideo(MultipartFile file) {
        UploadValidation.ValidatedUpload validated =
                UploadValidation.video(file, BaseContext.getCurrentId());
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(validated.objectKey()).normalize();
        if (!target.startsWith(base)) throw new RuntimeException("上传目录无效");
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return "/uploads/" + validated.objectKey();
        } catch (IOException e) {
            throw new RuntimeException("视频上传失败");
        }
    }

    private void validateSignature(MultipartFile file, String contentType) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(12); }
        boolean valid = switch (contentType) {
            case "image/png" -> header.length >= 8 && header[0] == (byte) 0x89 && header[1] == 0x50
                    && header[2] == 0x4e && header[3] == 0x47;
            case "image/jpeg" -> header.length >= 3 && header[0] == (byte) 0xff
                    && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff;
            case "image/gif" -> header.length >= 6 && header[0] == 'G' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == '8';
            case "image/webp" -> header.length >= 12 && header[0] == 'R' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                    && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!valid) throw new RuntimeException("文件内容与声明类型不一致");
    }

    private void validateDimensions(MultipartFile file, String contentType) throws IOException {
        if ("image/webp".equals(contentType)) return;
        BufferedImage image;
        try (InputStream input = file.getInputStream()) { image = ImageIO.read(input); }
        if (image == null) throw new RuntimeException("图片内容损坏");
        if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
            throw new RuntimeException("图片尺寸超出限制");
        }
    }

    private void validateVideoSignature(MultipartFile file, String contentType) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(16); }
        boolean valid = switch (contentType) {
            case "video/mp4", "video/quicktime", "video/x-m4v" -> header.length >= 12
                    && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            case "video/webm" -> header.length >= 4
                    && header[0] == 0x1a && header[1] == 0x45
                    && header[2] == (byte) 0xdf && header[3] == (byte) 0xa3;
            default -> false;
        };
        if (!valid) throw new RuntimeException("视频文件内容与声明格式不一致");
    }
}
