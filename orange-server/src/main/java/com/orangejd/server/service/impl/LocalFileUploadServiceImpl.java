package com.orangejd.server.service.impl;

import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileUploadServiceImpl implements FileUploadService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${orange.upload.path}")
    private String basePath;

    @Override
    public String upload(MultipartFile file, String subDir) {
        if (file.isEmpty()) throw new RuntimeException("文件为空");
        // 类型校验
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType))
            throw new RuntimeException("不支持的文件类型，仅允许 png/jpeg/gif/webp");
        // 大小校验
        if (file.getSize() > MAX_SIZE)
            throw new RuntimeException("文件过大，最大允许 10MB");
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".") ? original.substring(original.lastIndexOf(".")) : ".png";
        String name = UUID.randomUUID().toString().substring(0, 8) + ext;
        File dir = new File(basePath, subDir);
        if (!dir.exists()) dir.mkdirs();
        try {
            file.transferTo(new File(dir, name));
            return "/uploads/" + subDir + "/" + name;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
}
