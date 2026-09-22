package com.ai.mall.event;

/**
 * Tag 常量（单一事实源）：Tag=eventType，同一 Topic 下按 Tag 区分事件子类型。
 */
public final class EventTags {

    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    /** 延迟取消检查事件（order-delay Topic，Payload=OrderDelayPayload） */
    public static final String PAYMENT_TIMEOUT_CHECK = "PAYMENT_TIMEOUT_CHECK";

    private EventTags() {
    }
}
