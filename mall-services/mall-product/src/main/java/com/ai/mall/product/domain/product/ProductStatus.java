package com.ai.mall.product.domain.product;

/**
 * 商品状态生命周期。
 * DRAFT：草稿，创建后默认，商城不可见；
 * ON_SALE：上架可售（由 REQ-M2-003 发布动作触发）；
 * OFF_SALE：下架（由 REQ-M2-003 触发）；
 * DISABLED：禁用，不可被发布。
 */
public enum ProductStatus {
    DRAFT,
    ON_SALE,
    OFF_SALE,
    DISABLED;

    public static ProductStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("商品状态不能为空");
        }
        try {
            return ProductStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法商品状态: " + raw);
        }
    }
}
