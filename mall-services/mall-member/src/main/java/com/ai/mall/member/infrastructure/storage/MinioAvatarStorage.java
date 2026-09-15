package com.ai.mall.member.infrastructure.storage;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.MemberProfileErrorCode;
import com.ai.mall.member.application.port.AvatarStorage;
import com.ai.mall.member.domain.model.member.AvatarFormat;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * MinIO 头像存储实现（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>对象 key：{@code member-avatar/{memberId}/{uuid}.{ext}}——服务端按魔数判定结果重算扩展名，
 * 不采信客户端文件名。桶首次使用时懒创建并下发公开读 policy（s3:GetObject）。
 * MinIO 任何故障（含桶就绪失败/上传失败）统一转译 503 STORAGE_UNAVAILABLE，
 * 调用方保证先传对象成功再写库，不产生半成品 URL。
 */
public class MinioAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioAvatarStorage.class);

    private static final String KEY_PREFIX = "member-avatar/";
    private static final String BUCKET_POLICY_TEMPLATE = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*",\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    /** 桶懒就绪标志：首次上传时 ensure，就绪后短路；失败不置位以便下次请求重试。 */
    private volatile boolean bucketReady;

    public MinioAvatarStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String uploadAvatar(long memberId, byte[] content, AvatarFormat format) {
        ensureBucketReady();
        String objectKey = KEY_PREFIX + memberId + "/" + UUID.randomUUID() + "." + format.extension();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .contentType(format.contentType())
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception ex) {
            log.warn("MinIO 头像上传失败 memberId={} objectKey={}", memberId, objectKey, ex);
            throw new BusinessException(MemberProfileErrorCode.STORAGE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return publicUrl(objectKey);
    }

    /** 桶幂等就绪：不存在则创建并授予匿名公开读；任何故障 → 503（不影响注册登录主链）。 */
    private synchronized void ensureBucketReady() {
        if (bucketReady) {
            return;
        }
        String bucket = properties.bucket();
        try {
            boolean exists = Boolean.TRUE.equals(minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()));
            if (!exists) {
                try {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (Exception makeEx) {
                    // 并发首传竞态：对端已建即视为成功，其余按故障处理
                    if (!Boolean.TRUE.equals(minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build()))) {
                        throw makeEx;
                    }
                }
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(BUCKET_POLICY_TEMPLATE.formatted(bucket))
                        .build());
            }
            bucketReady = true;
        } catch (Exception ex) {
            log.warn("MinIO 头像桶就绪失败 bucket={}", bucket, ex);
            throw new BusinessException(MemberProfileErrorCode.STORAGE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String publicUrl(String objectKey) {
        String base = properties.publicBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + properties.bucket() + "/" + objectKey;
    }
}
