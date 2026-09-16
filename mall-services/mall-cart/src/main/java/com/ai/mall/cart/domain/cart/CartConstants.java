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

    /** 游客合并 token key 前缀（CHG-0018 DU-BE-803）。 */
    public static final String MERGE_TOKEN_KEY_PREFIX = "cart:merge:";

    /** 合并 token TTL：300 秒（一次性，合并后即删）。 */
    public static final long MERGE_TOKEN_TTL_SECONDS = 300L;

    /**
     * 库存三态阈值（DU-BE-802 读模型）：精确数量 0=缺货、1..（本值-1）=低库存、≥本值=充足。
     * 与 CHG-0017 mall-web StockBadge 三态口径保持同一 SSOT（常量 10）；
     * inventory 内部端点给精确数，由 cart 本地映射，不向会员浏览器暴露精确库存。
     */
    public static final long IN_STOCK_THRESHOLD = 10L;

    public static String memberKey(long memberId) {
        return MEMBER_KEY_PREFIX + memberId;
    }

    public static String mergeTokenKey(String token) {
        return MERGE_TOKEN_KEY_PREFIX + token;
    }
}
