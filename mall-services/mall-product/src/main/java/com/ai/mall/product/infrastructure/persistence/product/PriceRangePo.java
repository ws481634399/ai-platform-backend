package com.ai.mall.product.infrastructure.persistence.product;

/**
 * 启用 SKU 价区分组聚合投影：CHG-0015 商城列表价区（整数分）。
 *
 * @param productId 商品 SPU ID
 * @param minPrice  启用 SKU 最低价（分）
 * @param maxPrice  启用 SKU 最高价（分）
 */
public class PriceRangePo {

    private Long productId;
    private Long minPrice;
    private Long maxPrice;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Long minPrice) {
        this.minPrice = minPrice;
    }

    public Long getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Long maxPrice) {
        this.maxPrice = maxPrice;
    }
}
