package com.ai.mall.event.payload;

import java.time.Instant;

/**
 * ORDER_CANCELLED 事件负载（对齐 10-API与事件契约.md §41.3）。
 *
 * @param orderId       订单 ID
 * @param orderNo       订单号
 * @param reservationNo 库存预留号
 * @param cancelReason  取消原因（如 PAYMENT_TIMEOUT/USER_CANCELLED）
 * @param cancelledAt   取消时间
 */
public record OrderCancelledEventPayload(
        String orderId,
        String orderNo,
        String reservationNo,
        String cancelReason,
        Instant cancelledAt) {
}
