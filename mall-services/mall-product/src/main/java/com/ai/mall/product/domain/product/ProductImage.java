package com.ai.mall.product.domain.product;

/**
 * 商品图片：存 Object Key 与 URL，不落二进制。
 * mainFlag=true 表示主图，一个 Product 仅一张主图。
 */
public record ProductImage(
        long id,
        String objectKey,
        String imageUrl,
        ImageType imageType,
        int sortOrder,
        boolean mainFlag
) {
    public enum ImageType { MAIN, GALLERY, DETAIL, SKU }

    public ProductImage {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("图片 objectKey 不能为空");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("图片 URL 不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("图片排序不能为负");
        }
    }
}
