package com.ai.mall.search.domain.search;

/**
 * mall_products 索引读模型（CHG-0020 只读消费，字段与 CHG-0021 写模型冻结一致）。
 *
 * <p>docId = productId；mainImage 不建索引（index:false）；
 * 日期为 epoch 毫秒（仅用于 newest/默认排序，不进响应白名单）。
 */
public record SearchProductDocument(
        Long productId,
        String productName,
        String keywords,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String mainImage,
        String status,
        Long minPrice,
        Long maxPrice,
        Long publishedAt,
        Long updatedAt) {

    public static final String STATUS_ON_SALE = "ON_SALE";
}
