package com.ai.mall.event;

/**
 * Topic 常量（单一事实源）：业务服务禁止散落硬编码 topic 字符串。
 * <p>命名规范：统一前缀 aimall-（requirement-design §2.0 第 12 条）。</p>
 */
public final class EventTopics {

    /** 订单事件（ORDER_CREATED/PAYMENT_SUCCEEDED/ORDER_CANCELLED/ORDER_COMPLETED） */
    public static final String AIMALL_ORDER_EVENTS = "aimall-order-events";
    /** 订单延迟消息（PAYMENT_TIMEOUT_CHECK，延迟级别对应订单超时时间） */
    public static final String AIMALL_ORDER_DELAY = "aimall-order-delay";
    /** 库存事件（预留，M7 后续使用） */
    public static final String AIMALL_INVENTORY_EVENTS = "aimall-inventory-events";

    private EventTopics() {
    }
}
