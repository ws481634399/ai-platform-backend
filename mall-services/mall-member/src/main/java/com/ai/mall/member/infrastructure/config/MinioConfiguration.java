package com.ai.mall.member.infrastructure.config;

import com.ai.mall.member.infrastructure.storage.MinioAvatarStorage;
import com.ai.mall.member.infrastructure.storage.MinioStorageProperties;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储装配（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>{@link MinioClient} 构造不发起网络连接，MinIO 离线不影响服务启动与注册登录主链
 * （requirement-design §7）；bucket 就绪与上传均在首次头像写入时懒执行，故障映射 503。
 */
@Configuration
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioConfiguration {

    @Bean
    MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    MinioAvatarStorage minioAvatarStorage(MinioClient minioClient, MinioStorageProperties properties) {
        return new MinioAvatarStorage(minioClient, properties);
    }
}
