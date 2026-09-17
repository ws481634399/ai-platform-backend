package com.ai.mall.order.domain.order;

/**
 * 订单生命周期操作（与状态迁移、状态历史一一对应）。
 */
public enum OrderOperation {

    /** 创建订单（初始落库，fromStatus 为 null）。 */
    CREATE,
    /** 模拟支付成功。 */
    PAY,
    /** 会员主动取消。 */
    CANCEL,
    /** 后台发货。 */
    SHIP,
    /** 会员确认收货。 */
    CONFIRM_RECEIPT
}
