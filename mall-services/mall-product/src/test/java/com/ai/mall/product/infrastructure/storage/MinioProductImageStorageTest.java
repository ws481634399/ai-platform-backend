package com.ai.mall.product.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.application.image.ImageScene;
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
 * MinIO 商品图片存储单测（CHG-0023 STORY-007-01-01-02 / TC-002）：
 * mock SDK，不引入真实 MinIO——验证 scene 前缀 key、contentType、桶懒就绪幂等与 503 转译。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioProductImageStorage 上传/桶就绪/故障转译")
class MinioProductImageStorageTest {

    private static final Pattern BRAND_KEY_PATTERN =
            Pattern.compile("^brand/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
    private static final Pattern PRODUCT_KEY_PATTERN =
            Pattern.compile("^product/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png$");

    @Mock MinioClient minioClient;

    MinioStorageProperties properties =
            new MinioStorageProperties("http://localhost:9000", "ak", "sk", "mall-product",
                    "http://localhost:9000/");

    MinioProductImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioProductImageStorage(minioClient, properties);
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    }

    @Test
    @DisplayName("BRAND 首传：幂等建桶+公开读 policy+上传；key=brand/<uuid>.jpg、URL/contentType 合规")
    void brandFirstUploadCreatesBucketAndPutsObject() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        String url = storage.upload(ImageScene.BRAND, jpegBytes(), ImageFormat.JPEG);

        assertThat(url).startsWith("http://localhost:9000/mall-product/brand/").endsWith(".jpg");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<SetBucketPolicyArgs> policyCaptor = ArgumentCaptor.forClass(SetBucketPolicyArgs.class);
        verify(minioClient).setBucketPolicy(policyCaptor.capture());
        assertThat(policyCaptor.getValue().bucket()).isEqualTo("mall-product");
        assertThat(policyCaptor.getValue().config()).contains("s3:GetObject").contains("mall-product/*");

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().bucket()).isEqualTo("mall-product");
        assertThat(putCaptor.getValue().object()).matches(BRAND_KEY_PATTERN);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("PRODUCT 上传走 product/ 前缀与 png contentType；桶已存在跳过建桶")
    void productPrefixAndPngContentType() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        String url = storage.upload(ImageScene.PRODUCT, pngBytes(), ImageFormat.PNG);

        assertThat(url).startsWith("http://localhost:9000/mall-product/product/").endsWith(".png");
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().object()).matches(PRODUCT_KEY_PATTERN);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("webp 字节使用 image/webp contentType 与 .webp 扩展名，product 前缀")
    void webpContentTypeAndExtension() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);

        String url = storage.upload(ImageScene.PRODUCT, webp, ImageFormat.WEBP);

        assertThat(url).endsWith(".webp");
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/webp");
        assertThat(putCaptor.getValue().object()).startsWith("product/").endsWith(".webp");
    }

    @Test
    @DisplayName("桶懒就绪只执行一轮：第二次上传不再探桶/建桶")
    void existingBucketSkipsEnsureOnSecondUpload() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(
                org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        storage.upload(ImageScene.BRAND, jpegBytes(), ImageFormat.JPEG);
        storage.upload(ImageScene.PRODUCT, jpegBytes(), ImageFormat.JPEG);

        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("putObject 故障 → 503 S2101；桶已就绪，下次直接重试 put 成功")
    void putFailureMappedTo503() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IOException("connection refused"))
                .thenReturn(org.mockito.Mockito.mock(io.minio.ObjectWriteResponse.class));

        assertThatThrownBy(() -> storage.upload(ImageScene.BRAND, jpegBytes(), ImageFormat.JPEG))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("S2101");
                });

        storage.upload(ImageScene.BRAND, jpegBytes(), ImageFormat.JPEG);
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("桶就绪故障（bucketExists 抛错）→ 503，不调用 putObject")
    void bucketEnsureFailureMappedTo503() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> storage.upload(ImageScene.BRAND, jpegBytes(), ImageFormat.JPEG))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }
}
