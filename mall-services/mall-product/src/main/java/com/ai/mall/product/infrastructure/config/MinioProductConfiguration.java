package com.ai.mall.product.infrastructure.config;

import com.ai.mall.product.infrastructure.storage.MinioProductImageStorage;
import com.ai.mall.product.infrastructure.storage.MinioStorageProperties;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品图片 MinIO 对象存储装配（CHG-0023 STORY-007-01-01-02）。
 *
 * <p>{@link MinioClient} 构造不发起网络连接，MinIO 离线不影响服务启动与商品主链；
 * bucket 就绪与上传均在首次图片写入时懒执行，故障映射 503。
 * 与 mall-member 一致不加 {@code @Profile("!test")}：测试以 @MockitoBean 替换存储端口，
 * MinioClient 构造无网络副作用。
 */
@Configuration
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioProductConfiguration {

    @Bean
    MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    MinioProductImageStorage minioProductImageStorage(MinioClient minioClient,
                                                      MinioStorageProperties properties) {
        return new MinioProductImageStorage(minioClient, properties);
    }
}
