package com.ai.mall.cart.domain.cart;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 购物车域错误码（CHG-0018 DU-BE-801）。
 *
 * <p>B03xx 为购物车业务段（B01xx 会员资料、B02xx 收货地址已占用）；
 * S03xx 为购物车依赖/存储故障段。HTTP 语义由 BusinessException 携带。
 */
public enum CartErrorCode implements ErrorCode {

    /** 加购/合并后单 SKU 数量超过 999：400，购物车保持原值不变。 */
    CART_QUANTITY_LIMIT("B0301", "单品数量不能超过999件"),

    /** 不同 SKU 条目数超过 100：400，购物车保持原条目不变。 */
    CART_ITEMS_LIMIT("B0302", "购物车最多容纳100种商品"),

    /** 加购时商品非 ON_SALE 或 SKU 非 ENABLED 或不存在：400，车不变。 */
    SKU_NOT_SALABLE("B0303", "商品不可售或已失效"),

    /** 更新/勾选的购物车条目不存在：404。 */
    CART_ITEM_NOT_FOUND("B0304", "购物车中没有该商品"),

    /** 合并 token 不存在/已过期/已被消费：400（前端可重新取 token 重提）。 */
    MERGE_TOKEN_EXPIRED("B0305", "合并凭证已过期，请重新获取"),

    /** 合并 token 与当前会员不匹配（伪造/串号）：401。 */
    MERGE_TOKEN_INVALID("B0306", "合并凭证无效，请重新登录"),

    /** mall-product 内部契约调用故障（连接拒绝/5xx/非法响应）：503，区别于业务不可售。 */
    DEPENDENCY_UNAVAILABLE("S0301", "依赖服务暂不可用，请稍后重试"),

    /** Redis 读写故障：503。 */
    CART_STORAGE_UNAVAILABLE("S0302", "购物车服务暂不可用，请稍后重试");

    private final String code;
    private final String message;

    CartErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
