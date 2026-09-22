package com.ai.mall.event.payload;

import java.time.Instant;

/**
 * 延迟取消检查事件负载（order-delay Topic，PAYMENT_TIMEOUT_CHECK）。
 *
 * @param orderId  订单 ID
 * @param orderNo  订单号
 * @param expireAt 订单支付截止时间（超时检查基准）
 */
public record OrderDelayPayload(
        String orderId,
        String orderNo,
        Instant expireAt) {
}
