package com.ai.mall.member.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 存储配置（CHG-0016 STORY-003-01-02-01，前缀 {@code mall.storage.minio}）。
 *
 * @param endpoint       MinIO API 地址（SDK 调用），env MINIO_ENDPOINT
 * @param accessKey      访问密钥，env MINIO_ACCESS_KEY
 * @param secretKey      秘密钥，env MINIO_SECRET_KEY
 * @param bucket         头像桶（不存在由应用懒创建并授予公开读），env MINIO_AVATAR_BUCKET
 * @param publicBaseUrl  对外公开读前缀（可能经网关/反代，与 API endpoint 不同），
 *                       env MALL_MINIO_PUBLIC_BASE_URL
 */
@ConfigurationProperties(prefix = "mall.storage.minio")
public record MinioStorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl) {
}
