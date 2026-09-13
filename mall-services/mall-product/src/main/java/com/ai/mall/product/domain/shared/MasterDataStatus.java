package com.ai.mall.product.domain.shared;

/**
 * 主数据启停状态：分类、品牌共用同一组字符串值（ENABLED / DISABLED）。
 */
public enum MasterDataStatus {
    ENABLED,
    DISABLED;

    public static MasterDataStatus from(String value) {
        try {
            return valueOf(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("非法状态值: " + value);
        }
    }
}
