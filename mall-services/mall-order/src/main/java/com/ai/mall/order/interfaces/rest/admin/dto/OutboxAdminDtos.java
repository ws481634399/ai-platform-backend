package com.ai.mall.order.interfaces.rest.admin.dto;

import java.time.Instant;

/** 管理端 Outbox 事件视图。 */
public final class OutboxAdminDtos {

    private OutboxAdminDtos() {
    }

    public record OutboxView(
            Long id,
            String aggregateId,
            String eventType,
            int delayLevel,
            String payload,
            String status,
            int retryCount,
            Instant nextRetryAt,
            String traceId,
            String lastError,
            Instant createdAt,
            Instant sentAt
    ) {
    }
}
