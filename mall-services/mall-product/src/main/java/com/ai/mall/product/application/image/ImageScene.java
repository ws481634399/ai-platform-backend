package com.ai.mall.product.application.image;

/**
 * 商品图片业务场景（CHG-0023 STORY-007-01-01-02）。
 *
 * <p>决定对象存储 key 前缀：品牌素材 {@code brand/}、商品素材 {@code product/}。
 * 枚举名即 multipart 表单 scene 字段的合法取值（大小写敏感）。
 */
public enum ImageScene {

    /** 品牌 Logo 等品牌素材。 */
    BRAND("brand/"),

    /** 商品展示/详情/SKU 等商品素材。 */
    PRODUCT("product/");

    private final String keyPrefix;

    ImageScene(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    /**
     * 解析 scene 入参。
     *
     * @throws IllegalArgumentException null/空白/非枚举值
     */
    public static ImageScene from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scene is required");
        }
        try {
            return ImageScene.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid image scene: " + value);
        }
    }
}
