package com.ai.mall.order.application.compensation;

/**
 * 订单自动取消补偿载荷（CHG-0025 STORY-009-05-01）。
 *
 * <p>compensation_task 不扩列，eventId 冗余进 payload；traceId 由 compensation_task.trace_id 承载。</p>
 *
 * @param orderId 订单主键（systemCancel 按 id 加载）
 * @param orderNo 订单号（可读/对账）
 * @param eventId 原 PAYMENT_TIMEOUT_CHECK 事件 ID（链路追溯）
 */
public record OrderAutoCancelCompensationPayload(long orderId, String orderNo, String eventId) {
}
