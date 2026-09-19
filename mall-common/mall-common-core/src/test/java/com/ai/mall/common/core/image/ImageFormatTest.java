package com.ai.mall.common.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 共享图片格式魔数判定测试（CHG-0023 STORY-007-01-01-02 / TC-001）。
 * 等价迁移自 mall-member AvatarFormatTest，保证下沉后判定语义零变化。
 */
@DisplayName("共享 ImageFormat 魔数判定")
class ImageFormatTest {

    private static byte[] withMagic(byte[] magic) {
        byte[] content = new byte[Math.max(16, magic.length)];
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }

    @Test
    @DisplayName("jpeg/png/webp 魔数识别成功，返回对应 contentType/扩展名")
    void allowedFormatsDetected() {
        ImageFormat jpeg = ImageFormat.detect(withMagic(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertThat(jpeg).isEqualTo(ImageFormat.JPEG);
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.extension()).isEqualTo("jpg");

        ImageFormat png = ImageFormat.detect(withMagic(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        assertThat(png).isEqualTo(ImageFormat.PNG);
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extension()).isEqualTo("png");

        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);
        ImageFormat webpFormat = ImageFormat.detect(webp);
        assertThat(webpFormat).isEqualTo(ImageFormat.WEBP);
        assertThat(webpFormat.contentType()).isEqualTo("image/webp");
        assertThat(webpFormat.extension()).isEqualTo("webp");
    }

    @Test
    @DisplayName("伪装 gif（GIF89a 魔数）即使命名为 .png 也拒绝")
    void disguisedGifRejected() {
        byte[] gif = withMagic("GIF89a".getBytes());
        assertThatThrownBy(() -> ImageFormat.detect(gif))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RIFF 但非 WEBP（如 AVI/WAV）拒绝；空/null 内容拒绝；截断 webp 拒绝")
    void riffButNotWebpAndEmptyRejected() {
        assertThatThrownBy(() -> ImageFormat.detect(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageFormat.detect(null))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] riffOnly = withMagic("RIFF".getBytes());
        assertThatThrownBy(() -> ImageFormat.detect(riffOnly))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] webpTruncated = new byte[10];
        System.arraycopy("RIFF".getBytes(), 0, webpTruncated, 0, 4);
        assertThatThrownBy(() -> ImageFormat.detect(webpTruncated))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
