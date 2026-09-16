package com.ai.mall.cart.domain.cart;

/**
 * Lua 写脚本返回码（SSOT：story-design §4）。
 * 脚本只返回数字码，到业务异常的映射集中在仓储实现层。
 */
public enum CartScriptCode {

    /** 0：成功。 */
    OK(0),
    /** 1：合并/写入后数量超过 {@link CartConstants#MAX_QUANTITY}。 */
    QTY_LIMIT(1),
    /** 2：条目数达到 {@link CartConstants#MAX_ITEMS}。 */
    ITEMS_LIMIT(2),
    /** 3：目标条目不存在。 */
    NOT_FOUND(3);

    private final int code;

    CartScriptCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CartScriptCode of(long value) {
        for (CartScriptCode candidate : values()) {
            if (candidate.code == value) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("未知购物车脚本返回码: " + value);
    }
}
