package com.orangejd.server.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    String upload(MultipartFile file, String subDir);

    String uploadVideo(MultipartFile file);
}
