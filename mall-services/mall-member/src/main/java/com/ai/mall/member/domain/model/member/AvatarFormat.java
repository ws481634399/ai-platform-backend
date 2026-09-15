package com.ai.mall.member.domain.model.member;

import java.util.Arrays;

/**
 * 头像图片格式白名单（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>只允许 jpeg/png/webp；格式判定只读文件头魔数，不信任客户端上传的 contentType 与扩展名，
 * 以拦截「gif 改扩展名」等伪装文件。服务端按判定结果自行决定存储 contentType 与对象扩展名。
 */
public enum AvatarFormat {

    /** JPEG：{@code FF D8 FF} */
    JPEG("image/jpeg", "jpg",
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),

    /** PNG：8 字节签名 {@code 89 50 4E 47 0D 0A 1A 0A} */
    PNG("image/png", "png",
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),

    /** WEBP：偏移 0 的 "RIFF" 与偏移 8 的 "WEBP" 双段判定。 */
    WEBP("image/webp", "webp", null) {
        @Override
        boolean matches(byte[] content) {
            return content.length >= 12
                    && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
        }
    };

    private final String contentType;
    private final String extension;
    private final byte[] magic;

    AvatarFormat(String contentType, String extension, byte[] magic) {
        this.contentType = contentType;
        this.extension = extension;
        this.magic = magic;
    }

    /**
     * 按魔数判定图片格式。
     *
     * @throws IllegalArgumentException 非白名单格式（含伪装成图片的 gif 等）
     */
    public static AvatarFormat detect(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file is empty");
        }
        for (AvatarFormat format : values()) {
            if (format.matches(content)) {
                return format;
            }
        }
        throw new IllegalArgumentException("unsupported avatar image type");
    }

    /** 白名单格式魔数前缀比对（WEBP 覆盖为双段判定）。 */
    boolean matches(byte[] content) {
        return magic != null && content.length >= magic.length
                && Arrays.equals(Arrays.copyOf(content, magic.length), magic);
    }

    public String contentType() {
        return contentType;
    }

    /** 服务端重算的对象扩展名，禁止使用客户端文件名。 */
    public String extension() {
        return extension;
    }
}
