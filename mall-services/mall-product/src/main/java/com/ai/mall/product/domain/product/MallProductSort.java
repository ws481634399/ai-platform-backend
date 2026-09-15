package com.ai.mall.product.domain.product;

/**
 * 商城商品列表排序方式（白名单，禁止字符串拼接入 SQL）。
 */
public enum MallProductSort {
    /** 默认：创建时间倒序 */
    DEFAULT,
    /** 新品：同默认（创建时间倒序） */
    NEWEST,
    /** 价升：启用 SKU 最低价升序，同分按创建时间倒序 */
    PRICE_ASC,
    /** 价降：启用 SKU 最高价降序，同分按创建时间倒序 */
    PRICE_DESC;

    /** 非法值静默回落 DEFAULT。 */
    public static MallProductSort from(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT;
        try {
            return MallProductSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
