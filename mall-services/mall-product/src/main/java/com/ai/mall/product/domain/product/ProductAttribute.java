package com.ai.mall.product.domain.product;

/**
 * 商品属性（非规格键值对，如 材质=纯棉）。
 */
public record ProductAttribute(
        long id,
        String name,
        String value,
        int sortOrder
) {
    public ProductAttribute {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("属性名不能为空");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("属性值不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("属性排序不能为负");
        }
    }
}
