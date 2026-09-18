package com.ai.mall.search.application.index.projection;

/**
 * 商品搜索投影（CHG-0021 数据契约，与 mall-product /api/internal 端点同构）。
 *
 * <p>productId 以 String 传输（跨语言/序列化稳定）；价格整数分；
 * 时间 epoch 毫秒，updatedAt 兼作 external_gte 版本号；keywords M5 固定空串（列预留）。
 */
public record ProductProjectionView(
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
