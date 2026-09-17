package com.ai.mall.order.domain.order;

import java.time.Instant;

/**
 * 订单状态历史（审计/排错/轨迹展示，CHG-0019）。
 *
 * <p>状态迁移与 history 插入必须在同一事务；CREATE 的 fromStatus 为 null。
 */
public class OrderStatusHistory {

    private Long id;
    private final long orderId;
    private final String orderNo;
    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;
    private final OrderOperation operation;
    private final String operator;
    private final String reason;
    private final Instant occurredAt;

    public OrderStatusHistory(long orderId, String orderNo, OrderStatus fromStatus, OrderStatus toStatus,
                              OrderOperation operation, String operator, String reason, Instant occurredAt) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operation = operation;
        this.operator = operator;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public static OrderStatusHistory reconstitute(Long id, long orderId, String orderNo, OrderStatus fromStatus,
                                                  OrderStatus toStatus, OrderOperation operation, String operator,
                                                  String reason, Instant occurredAt) {
        OrderStatusHistory history = new OrderStatusHistory(orderId, orderNo, fromStatus, toStatus,
                operation, operator, reason, occurredAt);
        history.id = id;
        return history;
    }

    void assignPersistedId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public long orderId() { return orderId; }
    public String orderNo() { return orderNo; }
    public OrderStatus fromStatus() { return fromStatus; }
    public OrderStatus toStatus() { return toStatus; }
    public OrderOperation operation() { return operation; }
    public String operator() { return operator; }
    public String reason() { return reason; }
    public Instant occurredAt() { return occurredAt; }
}
