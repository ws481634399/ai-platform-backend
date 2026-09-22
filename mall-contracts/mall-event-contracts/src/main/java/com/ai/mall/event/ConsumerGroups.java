package com.ai.mall.event;

/**
 * 消费者组常量（单一事实源）。
 */
public final class ConsumerGroups {

    /** mall-inventory 消费订单事件（PAYMENT_SUCCEEDED/ORDER_CANCELLED） */
    public static final String INVENTORY_CONSUMER_GROUP = "inventory-consumer-group";
    /** mall-order 延迟取消检查消费者 */
    public static final String ORDER_DELAY_CONSUMER_GROUP = "order-delay-consumer-group";

    private ConsumerGroups() {
    }
}
