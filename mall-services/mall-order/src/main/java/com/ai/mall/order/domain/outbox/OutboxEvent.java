package com.ai.mall.order.domain.outbox;

import java.time.Instant;

/**
 * Outbox 事件领域实体：业务事务内写入，独立任务异步投递。
 *
 * <p>payload 存储完整 Envelope JSON（含七字段），投递时直接反序列化发送。</p>
 */
public class OutboxEvent {

    private Long id;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private OutboxStatus status;
    private int retryCount;
    private Instant nextRetryAt;
    private final String traceId;
    private String lastError;
    private final Instant createdAt;
    private Instant sentAt;

    public OutboxEvent(String aggregateId, String eventType, String payload, String traceId) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.traceId = traceId;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    private OutboxEvent(Long id, String aggregateId, String eventType, String payload,
                        OutboxStatus status, int retryCount, Instant nextRetryAt,
                        String traceId, String lastError, Instant createdAt, Instant sentAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.traceId = traceId;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public static OutboxEvent reconstitute(Long id, String aggregateId, String eventType, String payload,
                                           OutboxStatus status, int retryCount, Instant nextRetryAt,
                                           String traceId, String lastError, Instant createdAt, Instant sentAt) {
        return new OutboxEvent(id, aggregateId, eventType, payload, status, retryCount, nextRetryAt,
                traceId, lastError, createdAt, sentAt);
    }

    public void assignPersistedId(Long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getTraceId() { return traceId; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
