package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.FileUploadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "orange.oss.enabled", havingValue = "true")
public class OssFileUploadServiceImpl implements FileUploadService {
    private static final String MEDIA_PREFIX = "/api/media/";
    private final OssObjectStorage storage;

    public OssFileUploadServiceImpl(OssObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public String upload(MultipartFile file, String subDir) {
        return upload(file, UploadValidation.image(file, subDir, BaseContext.getCurrentId()));
    }

    @Override
    public String uploadVideo(MultipartFile file) {
        return upload(file, UploadValidation.video(file, BaseContext.getCurrentId()));
    }

    private String upload(MultipartFile file, UploadValidation.ValidatedUpload validated) {
        try (InputStream input = file.getInputStream()) {
            storage.put(validated.objectKey(), validated.contentType(), file.getSize(), input);
            return MEDIA_PREFIX + validated.objectKey();
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
}
