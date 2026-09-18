package com.ai.mall.search.domain.search;

/**
 * 搜索排序模式（CHG-0020，冻结四种）。
 * {@link #from(String)} 对未知值安全回退 {@link #DEFAULT}（调用方无需先判空）。
 */
public enum SortMode {

    DEFAULT,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST;

    public static SortMode from(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        return switch (raw.trim()) {
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            case "newest" -> NEWEST;
            default -> DEFAULT;
        };
    }
}
