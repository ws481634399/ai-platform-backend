package com.ai.mall.event.payload;

import java.time.Instant;

/**
 * ORDER_COMPLETED 事件负载（对齐 10-API与事件契约.md §41.4）。
 *
 * @param orderId     订单 ID
 * @param orderNo     订单号
 * @param memberId    会员 ID
 * @param completedAt 完成时间
 */
public record OrderCompletedEventPayload(
        String orderId,
        String orderNo,
        String memberId,
        Instant completedAt) {
}
