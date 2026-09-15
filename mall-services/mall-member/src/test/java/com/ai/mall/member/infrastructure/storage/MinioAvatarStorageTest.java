package com.ai.mall.member.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.domain.model.member.AvatarFormat;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * MinIO 头像存储单测（CHG-0016 STORY-003-01-02-01 / TC-004、TC-005）：
 * mock SDK，不引入真实 MinIO/Testcontainer——验证 key 规则、contentType、桶懒就绪幂等与 503 转译。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioAvatarStorage 上传/桶就绪/故障转译")
class MinioAvatarStorageTest {

    private static final long MEMBER_ID = 72000001L;
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^member-avatar/72000001/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");

    @Mock MinioClient minioClient;

    MinioStorageProperties properties =
            new MinioStorageProperties("http://localhost:9000", "ak", "sk", "mall-avatar",
                    "http://localhost:9000/");

    MinioAvatarStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioAvatarStorage(minioClient, properties);
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    @Test
    @DisplayName("TC-004 桶不存在：幂等建桶+公开读 policy+上传；key/contentType/URL 合规")
    void firstUploadCreatesBucketPolicyAndPutsObject() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        String url = storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG);

        assertThat(url).startsWith("http://localhost:9000/mall-avatar/member-avatar/72000001/")
                .endsWith(".jpg");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<SetBucketPolicyArgs> policyCaptor = ArgumentCaptor.forClass(SetBucketPolicyArgs.class);
        verify(minioClient).setBucketPolicy(policyCaptor.capture());
        assertThat(policyCaptor.getValue().bucket()).isEqualTo("mall-avatar");
        assertThat(policyCaptor.getValue().config()).contains("s3:GetObject").contains("mall-avatar/*");

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        PutObjectArgs args = putCaptor.getValue();
        assertThat(args.bucket()).isEqualTo("mall-avatar");
        assertThat(args.object()).matches(KEY_PATTERN);
        assertThat(args.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("TC-004 桶已存在：跳过建桶/policy；第二次上传只做 putObject（懒就绪只执行一轮）")
    void existingBucketSkipsEnsureOnSecondUpload() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG);
        storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG);

        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("TC-004 webp 字节使用 image/webp contentType 与 .webp 扩展名")
    void webpContentTypeAndExtension() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);

        String url = storage.uploadAvatar(MEMBER_ID, webp, AvatarFormat.WEBP);

        assertThat(url).endsWith(".webp");
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/webp");
        assertThat(putCaptor.getValue().object()).endsWith(".webp");
    }

    @Test
    @DisplayName("TC-005 putObject 故障 → 503 STORAGE_UNAVAILABLE；桶已就绪，下次直接重试 put")
    void putFailureMappedTo503() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IOException("connection refused"))
                .thenReturn(org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        assertThatThrownBy(() -> storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("S0102");
                });

        // 桶探测此前已成功 → ready 置位；第二次上传不重复探桶，直接重试 putObject 并成功
        storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG);
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("桶就绪故障（bucketExists 抛错）→ 503，不调用 putObject")
    void bucketEnsureFailureMappedTo503() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> storage.uploadAvatar(MEMBER_ID, jpegBytes(), AvatarFormat.JPEG))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }
}
