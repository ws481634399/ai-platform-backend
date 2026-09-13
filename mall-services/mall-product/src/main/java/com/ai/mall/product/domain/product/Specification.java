package com.ai.mall.product.domain.product;

/**
 * 规格键值对（决定 SKU 差异，如 颜色=黑色）。
 */
public record Specification(String name, String value) {

    public Specification {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("规格名不能为空");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("规格值不能为空");
        }
    }
}
