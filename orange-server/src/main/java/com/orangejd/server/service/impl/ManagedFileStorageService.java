package com.orangejd.server.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.orangejd.server.mapper.StorageCleanupMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ManagedFileStorageService {
    private static final Pattern OWNED_MEDIA = Pattern.compile(
            "^/(?:api/media|uploads)/(general|cover|detail|avatar|video)/([1-9][0-9]*)/([0-9a-fA-F-]{36}\\.(?:png|jpg|gif|webp|mp4|mov|m4v|webm))$");
    private static final Pattern DELIVERY_OBJECT = Pattern.compile(
            "^delivery/([1-9][0-9]*)/([1-9][0-9]*)/([0-9a-fA-F-]{36}\\.(?:pdf|docx|xlsx|pptx|txt|zip))$");
    private static final Pattern OWNED_MEDIA_IN_TEXT = Pattern.compile(
            "/(?:api/media|uploads)/(?:general|cover|detail|avatar|video)/([1-9][0-9]*)/[0-9a-fA-F-]{36}\\.(?:png|jpg|gif|webp|mp4|mov|m4v|webm)");

    private final ObjectProvider<OssObjectStorage> ossProvider;
    private final Path localBase;
    private final StorageCleanupMapper cleanupMapper;

    public ManagedFileStorageService(ObjectProvider<OssObjectStorage> ossProvider,
                                     @Value("${orange.upload.path}") String uploadPath,
                                     StorageCleanupMapper cleanupMapper) {
        this.ossProvider = ossProvider;
        this.localBase = Paths.get(uploadPath).toAbsolutePath().normalize();
        this.cleanupMapper = cleanupMapper;
    }

    public StoredAttachment uploadDelivery(MultipartFile file, Long orderId, Long ownerId) {
        UploadValidation.ValidatedAttachment validated = UploadValidation.attachment(file, orderId, ownerId);
        OssObjectStorage oss = ossProvider.getIfAvailable();
        try (InputStream input = file.getInputStream()) {
            if (oss != null) {
                oss.put(validated.objectKey(), validated.contentType(), validated.size(), input,
                        validated.originalName());
            } else {
                Path target = safeLocalPath(validated.objectKey());
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
            }
        } catch (IOException e) {
            throw new RuntimeException("交付附件上传失败", e);
        }
        return new StoredAttachment(validated.objectKey(), validated.originalName(),
                validated.contentType(), validated.size());
    }

    public String createDownloadUrl(String objectKey, Long orderId) {
        requireDeliveryOrder(objectKey, orderId);
        OssObjectStorage oss = ossProvider.getIfAvailable();
        if (oss != null) return oss.signedGetUrl(objectKey).toString();
        return "/uploads/" + objectKey;
    }

    public boolean existsDelivery(String objectKey, Long orderId, Long ownerId) {
        requireDeliveryOwner(objectKey, orderId, ownerId);
        OssObjectStorage oss = ossProvider.getIfAvailable();
        return oss != null ? oss.exists(objectKey) : Files.isRegularFile(safeLocalPath(objectKey));
    }

    public void deleteDelivery(String objectKey, Long orderId, Long ownerId) {
        requireDeliveryOwner(objectKey, orderId, ownerId);
        deleteObject(objectKey);
    }

    public void deleteOwnedMediaAfterCommit(Long ownerId, Collection<String> urls) {
        if (ownerId == null || urls == null || urls.isEmpty()) return;
        Set<String> objectKeys = new LinkedHashSet<>();
        for (String url : urls) {
            Matcher matcher = url == null ? null : OWNED_MEDIA.matcher(url.trim());
            if (matcher != null && matcher.matches() && ownerId.toString().equals(matcher.group(2))) {
                objectKeys.add(matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3));
            }
        }
        if (objectKeys.isEmpty()) return;
        Runnable cleanup = () -> objectKeys.forEach(this::deleteObjectQuietly);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    public Set<String> findOwnedMediaUrls(Long ownerId, String text) {
        Set<String> urls = new LinkedHashSet<>();
        if (ownerId == null || text == null || text.isBlank()) return urls;
        Matcher matcher = OWNED_MEDIA_IN_TEXT.matcher(text);
        while (matcher.find()) {
            if (ownerId.toString().equals(matcher.group(1))) urls.add(matcher.group());
        }
        return urls;
    }

    public static void requireDeliveryOwner(String objectKey, Long orderId, Long ownerId) {
        Matcher matcher = objectKey == null ? null : DELIVERY_OBJECT.matcher(objectKey.trim());
        if (matcher == null || !matcher.matches()
                || orderId == null || ownerId == null
                || !orderId.toString().equals(matcher.group(1))
                || !ownerId.toString().equals(matcher.group(2))) {
            throw new IllegalArgumentException("交付附件地址无效");
        }
    }

    private static void requireDeliveryOrder(String objectKey, Long orderId) {
        Matcher matcher = objectKey == null ? null : DELIVERY_OBJECT.matcher(objectKey.trim());
        if (matcher == null || !matcher.matches() || orderId == null
                || !orderId.toString().equals(matcher.group(1))) {
            throw new IllegalArgumentException("交付附件地址无效");
        }
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            deleteObject(objectKey);
        } catch (RuntimeException e) {
            log.error("存储对象清理失败 objectKey={}", objectKey, e);
            cleanupMapper.enqueue(objectKey, errorMessage(e));
        }
    }

    public void retryQueuedDelete(String objectKey) {
        if (!isManagedObjectKey(objectKey)) throw new IllegalArgumentException("清理对象路径无效");
        deleteObject(objectKey);
    }

    private boolean isManagedObjectKey(String objectKey) {
        return objectKey != null && (DELIVERY_OBJECT.matcher(objectKey).matches()
                || OWNED_MEDIA.matcher("/api/media/" + objectKey).matches());
    }

    private String errorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private void deleteObject(String objectKey) {
        OssObjectStorage oss = ossProvider.getIfAvailable();
        if (oss != null) oss.delete(objectKey);
        else {
            try { Files.deleteIfExists(safeLocalPath(objectKey)); }
            catch (IOException e) { throw new RuntimeException("本地文件删除失败", e); }
        }
    }

    private Path safeLocalPath(String objectKey) {
        Path target = localBase.resolve(objectKey).normalize();
        if (!target.startsWith(localBase)) throw new IllegalArgumentException("文件路径无效");
        return target;
    }

    public record StoredAttachment(String objectKey, String originalName, String contentType, long size) {}
}
