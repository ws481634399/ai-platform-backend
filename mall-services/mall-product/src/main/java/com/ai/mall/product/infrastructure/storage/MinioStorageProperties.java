package com.ai.mall.product.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商品图片 MinIO 存储配置（CHG-0023 STORY-007-01-01-02，前缀 {@code mall.storage.minio}，
 * 与 mall-member 头像通道同前缀同形状、不同包各自声明）。
 *
 * @param endpoint      MinIO API 地址（SDK 调用），env MINIO_ENDPOINT
 * @param accessKey     访问密钥，env MINIO_ACCESS_KEY
 * @param secretKey     秘密钥，env MINIO_SECRET_KEY
 * @param bucket        商品图片桶（不存在由应用懒创建并授予公开读），env MINIO_PRODUCT_BUCKET
 * @param publicBaseUrl 对外公开读前缀（可能经网关/反代，与 API endpoint 不同）
 */
@ConfigurationProperties(prefix = "mall.storage.minio")
public record MinioStorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl) {
}
