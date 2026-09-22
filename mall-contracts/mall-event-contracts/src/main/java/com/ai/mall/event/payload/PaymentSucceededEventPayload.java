package com.ai.mall.event.payload;

import java.time.Instant;

/**
 * PAYMENT_SUCCEEDED 事件负载（对齐 10-API与事件契约.md §41.2）。
 *
 * @param orderId       订单 ID
 * @param orderNo       订单号
 * @param paymentNo     支付单号
 * @param reservationNo 库存预留号
 * @param paymentAmount 支付金额（分）
 * @param currency      币种（如 CNY）
 * @param paidAt        支付成功时间
 */
public record PaymentSucceededEventPayload(
        String orderId,
        String orderNo,
        String paymentNo,
        String reservationNo,
        Long paymentAmount,
        String currency,
        Instant paidAt) {
}
