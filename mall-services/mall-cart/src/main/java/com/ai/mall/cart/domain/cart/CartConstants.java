package com.ai.mall.cart.domain.cart;

/**
 * 购物车领域常量（CHG-0018 DU-BE-801）。
 *
 * <p>key 空间 {@code cart:member:{memberId}} 为会员车 Hash；
 * 上限与 TTL 为写模型硬约束，Lua 脚本与应用层共同兜底。
 */
public final class CartConstants {

    private CartConstants() {
    }

    /** 购物车条目数上限（不同 SKU 数，HLEN 判定）。 */
    public static final int MAX_ITEMS = 100;

    /** 单 SKU 数量上限（含加购合并后的总量）。 */
    public static final int MAX_QUANTITY = 999;

    /** 写后滑动 TTL：90 天（秒），每次写在 Lua 内 EXPIRE 续期。 */
    public static final long TTL_SECONDS = 90L * 24 * 60 * 60;

    /** 会员车 key 前缀。 */
    public static final String MEMBER_KEY_PREFIX = "cart:member:";

    public static String memberKey(long memberId) {
        return MEMBER_KEY_PREFIX + memberId;
    }
}
