package com.ai.mall.order.domain.order.event;

/**
 * 订单集成事件类型（领域层声明）。
 *
 * <p>值与 mall-event-contracts 的 EventTags 常量一一对应；领域层不反向依赖契约包，
 * 由 application 层的 flusher 完成到 Tag 字符串的映射。
 * 新增事件类型须先改 §41 契约文档与 EventTags，再扩展本枚举。
 */
public enum OrderIntegrationEventType {
    ORDER_CREATED,
    PAYMENT_SUCCEEDED,
    ORDER_CANCELLED,
    ORDER_COMPLETED,
    PAYMENT_TIMEOUT_CHECK
}
