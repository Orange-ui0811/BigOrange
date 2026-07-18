package com.orangejd.server.service.impl;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.orangejd.common.properties.OssProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

@Component
@ConditionalOnProperty(name = "orange.oss.enabled", havingValue = "true")
public class OssObjectStorage {
    private final OssProperties properties;
    private final OSS uploadClient;
    private final OSS publicClient;

    public OssObjectStorage(OssProperties properties) {
        validate(properties);
        this.properties = properties;
        this.uploadClient = buildClient(properties, properties.getEndpoint());
        this.publicClient = buildClient(properties, properties.getPublicEndpoint());
    }

    public void put(String objectKey, String contentType, long contentLength, InputStream input) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);
        uploadClient.putObject(properties.getBucketName(), objectKey, input, metadata);
    }

    public URL signedGetUrl(String objectKey) {
        Date expiresAt = Date.from(Instant.now().plusSeconds(properties.getSignedUrlTtlSeconds()));
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucketName(), objectKey, HttpMethod.GET);
        request.setExpiration(expiresAt);
        return publicClient.generatePresignedUrl(request);
    }

    @PreDestroy
    public void close() {
        uploadClient.shutdown();
        publicClient.shutdown();
    }

    private static OSS buildClient(OssProperties properties, String endpoint) {
        validate(properties);
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(new DefaultCredentialProvider(
                        properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .clientConfiguration(configuration)
                .region(properties.getRegion())
                .build();
    }

    private static void validate(OssProperties properties) {
        require("OSS_ENDPOINT", properties.getEndpoint());
        require("OSS_PUBLIC_ENDPOINT", properties.getPublicEndpoint());
        require("OSS_REGION", properties.getRegion());
        require("OSS_ACCESS_KEY", properties.getAccessKeyId());
        require("OSS_ACCESS_SECRET", properties.getAccessKeySecret());
        require("OSS_BUCKET", properties.getBucketName());
        if (properties.getSignedUrlTtlSeconds() < 60 || properties.getSignedUrlTtlSeconds() > 3600) {
            throw new IllegalStateException("OSS_SIGNED_URL_TTL_SECONDS 必须在 60 到 3600 之间");
        }
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " 未配置");
    }
}
