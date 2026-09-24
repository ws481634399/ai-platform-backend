package com.ai.mall.order.domain.outbox;

/** Outbox 事件投递状态机。 */
public enum OutboxStatus {
    /** 待投递 */
    PENDING,
    /** 已被某实例 CAS 抢占，正在发送 */
    SENDING,
    /** 已成功投递到 Broker */
    SENT,
    /** 超过最大重试次数，需人工介入 */
    FAILED
}
