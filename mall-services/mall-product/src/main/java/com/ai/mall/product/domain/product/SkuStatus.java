package com.ai.mall.product.domain.product;

/**
 * SKU 状态：ENABLED 可售 / DISABLED 停用。
 */
public enum SkuStatus {
    ENABLED,
    DISABLED;

    public static SkuStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SKU 状态不能为空");
        }
        try {
            return SkuStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法 SKU 状态: " + raw);
        }
    }
}
