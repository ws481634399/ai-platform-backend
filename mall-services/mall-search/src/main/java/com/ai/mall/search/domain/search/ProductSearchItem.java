package com.ai.mall.search.domain.search;

/**
 * 搜索结果摘要项（CHG-0020 响应白名单冻结）。
 *
 * <p>仅含商品卡片所需摘要字段，禁止携带 SKU 列表等聚合全字段；
 * 价格为整数分（minPrice/maxPrice 为启用 SKU 价格极值）。
 */
public record ProductSearchItem(
        Long productId,
        String productName,
        String mainImage,
        Long minPrice,
        Long maxPrice,
        String brandName,
        String categoryName) {
}
