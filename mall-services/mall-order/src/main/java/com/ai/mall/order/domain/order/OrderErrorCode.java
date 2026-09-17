package com.ai.mall.order.domain.order;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 订单域错误码（CHG-0019）。
 *
 * <p>B04xx 为订单交易业务段（B03xx 购物车）；S04xx 为订单依赖/存储故障段。
 * HTTP 语义由 BusinessException 携带。
 */
public enum OrderErrorCode implements ErrorCode {

    /** 订单不存在（或会员侧越权访问他人订单，统一不区分）：404。 */
    ORDER_NOT_FOUND("B0401", "订单不存在"),

    /** 商品已下架/删除或 SKU 已禁用/不存在：400。 */
    PRODUCT_NOT_SALABLE("B0402", "商品已失效或不可售"),

    /** SKU 参数非法（id 非法/数量超限/重复行）：400。 */
    SKU_INVALID("B0403", "商品信息不合法"),

    /** 库存不足导致建单失败：409（不产生待支付订单）。 */
    INSUFFICIENT_STOCK("B0404", "库存不足"),

    /** 收货地址不存在或不属于当前会员：404（不泄露归属差异）。 */
    ADDRESS_NOT_OWNED("B0405", "收货地址不存在"),

    /** 下单令牌不存在/已过期/已消费/与请求不匹配：400（引导重新预览）。 */
    SUBMIT_TOKEN_INVALID("B0406", "下单凭证已失效，请重新确认订单"),

    /** 支付/取消竞争失败或当前状态不允许该操作：409。 */
    STATUS_CONFLICT("B0407", "当前订单状态不允许该操作"),

    /** 补偿任务不存在：404。 */
    COMPENSATION_NOT_FOUND("B0408", "补偿任务不存在"),

    /** 预览/下单商品行为空或超量：400。 */
    ORDER_ITEMS_INVALID("B0410", "订单商品信息不合法"),

    /** 下游服务（product/inventory/member/cart）调用故障：503，区别于业务拒绝。 */
    DEPENDENCY_UNAVAILABLE("S0401", "依赖服务暂不可用，请稍后重试"),

    /** 锁库存成功后订单落库失败等内部故障：500（已触发库存补偿）。 */
    ORDER_CREATE_FAILED("S0402", "订单创建失败，请稍后重试");

    private final String code;
    private final String message;

    OrderErrorCode(String code, String message) {
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
