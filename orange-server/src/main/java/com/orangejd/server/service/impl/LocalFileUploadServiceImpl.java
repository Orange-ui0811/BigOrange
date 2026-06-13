package com.orangejd.server.service.impl;

import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class LocalFileUploadServiceImpl implements FileUploadService {

    @Value("${orange.upload.path}")
    private String basePath;

    @Override
    public String upload(MultipartFile file, String subDir) {
        if (file.isEmpty()) throw new RuntimeException("文件为空");
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
