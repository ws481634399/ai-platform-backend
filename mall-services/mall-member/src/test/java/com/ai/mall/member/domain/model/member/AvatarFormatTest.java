package com.ai.mall.member.domain.model.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 头像魔数白名单判定测试（CHG-0016 STORY-003-01-02-01 / TC-005）。
 */
@DisplayName("头像格式魔数判定")
class AvatarFormatTest {

    private static byte[] withMagic(byte[] magic) {
        byte[] content = new byte[Math.max(16, magic.length)];
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }

    @Test
    @DisplayName("TC-005 jpeg/png/webp 魔数识别成功，返回对应 contentType/扩展名")
    void allowedFormatsDetected() {
        AvatarFormat jpeg = AvatarFormat.detect(withMagic(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertThat(jpeg).isEqualTo(AvatarFormat.JPEG);
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.extension()).isEqualTo("jpg");

        AvatarFormat png = AvatarFormat.detect(withMagic(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        assertThat(png).isEqualTo(AvatarFormat.PNG);
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extension()).isEqualTo("png");

        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);
        AvatarFormat webpFormat = AvatarFormat.detect(webp);
        assertThat(webpFormat).isEqualTo(AvatarFormat.WEBP);
        assertThat(webpFormat.contentType()).isEqualTo("image/webp");
        assertThat(webpFormat.extension()).isEqualTo("webp");
    }

    @Test
    @DisplayName("TC-005 伪装 gif（GIF89a 魔数）即使命名为 .png 也拒绝")
    void disguisedGifRejected() {
        byte[] gif = withMagic("GIF89a".getBytes());
        assertThatThrownBy(() -> AvatarFormat.detect(gif))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RIFF 但非 WEBP（如 AVI/WAV）拒绝；空内容拒绝；截断 webp 拒绝")
    void riffButNotWebpAndEmptyRejected() {
        assertThatThrownBy(() -> AvatarFormat.detect(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AvatarFormat.detect(null))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] riffOnly = withMagic("RIFF".getBytes());
        assertThatThrownBy(() -> AvatarFormat.detect(riffOnly))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] webpTruncated = new byte[10];
        System.arraycopy("RIFF".getBytes(), 0, webpTruncated, 0, 4);
        assertThatThrownBy(() -> AvatarFormat.detect(webpTruncated))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
