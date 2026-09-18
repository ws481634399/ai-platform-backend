package com.ai.mall.product.application.search;

/**
 * 搜索投影出站视图（CHG-0021）：与 mall-search ProductProjectionView 字段同名同序，
 * productId 字符串传输，时间 epoch 毫秒（updatedAt 兼作 ES external_gte 版本）。
 */
public record SearchProjectionView(
        String productId,
        String productName,
        String keywords,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String mainImage,
        String status,
        Long minPriceFen,
        Long maxPriceFen,
        Long publishedAt,
        Long updatedAt) {
}
