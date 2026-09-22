package com.ai.mall.event.payload;

import java.time.Instant;

/**
 * ORDER_CREATED 事件负载（对齐 10-API与事件契约.md §41.1）。
 *
 * @param orderId         订单 ID
 * @param orderNo         订单号
 * @param memberId        会员 ID
 * @param reservationNo   库存预留号
 * @param orderAmount     订单金额（分）
 * @param currency        币种（如 CNY）
 * @param paymentDeadline 最晚支付时间
 */
public record OrderCreatedEventPayload(
        String orderId,
        String orderNo,
        String memberId,
        String reservationNo,
        Long orderAmount,
        String currency,
        Instant paymentDeadline) {
}
