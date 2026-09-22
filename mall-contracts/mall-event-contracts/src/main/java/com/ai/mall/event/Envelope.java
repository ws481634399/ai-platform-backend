package com.ai.mall.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * 集成事件信封（对齐 02-统一语言词汇表.md §19）：跨服务事件传输的统一外层结构。
 * <p>七字段契约：eventId/eventType/eventVersion/occurredAt/producer/traceId/payload。</p>
 * <p>约束：纯 POJO，禁止业务逻辑与 Spring 依赖；消息 keys=eventId，Tag=eventType。</p>
 */
public final class Envelope {

    /** 事件唯一 ID（UUID），消费幂等键 */
    private final String eventId;
    /** 事件类型（与消息 Tag 一致，如 ORDER_CREATED） */
    private final String eventType;
    /** 事件 schema 版本（当前 v1） */
    private final int eventVersion;
    /** 事件发生时间 */
    private final Instant occurredAt;
    /** 生产者服务名（如 mall-order） */
    private final String producer;
    /** 链路追踪 ID；允许为空——发送侧从 MDC 读取，缺失时由 mall-mq 生产者自动生成 */
    private final String traceId;
    /** 业务负载（JsonNode 保持 schema 自由，各 eventType 对应 Payload DTO 见 payload 包） */
    private final JsonNode payload;

    @JsonCreator
    private Envelope(@JsonProperty("eventId") String eventId,
                     @JsonProperty("eventType") String eventType,
                     @JsonProperty("eventVersion") int eventVersion,
                     @JsonProperty("occurredAt") Instant occurredAt,
                     @JsonProperty("producer") String producer,
                     @JsonProperty("traceId") String traceId,
                     @JsonProperty("payload") JsonNode payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.occurredAt = occurredAt;
        this.producer = producer;
        this.traceId = traceId;
        this.payload = payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public String getTraceId() {
        return traceId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Envelope{eventId=" + eventId + ", eventType=" + eventType
                + ", eventVersion=" + eventVersion + ", occurredAt=" + occurredAt
                + ", producer=" + producer + ", traceId=" + traceId + "}";
    }

    /** build 工厂：必填字段缺失快速失败，防止残缺事件进入消息管道 */
    public static final class Builder {

        private String eventId;
        private String eventType;
        private int eventVersion;
        private Instant occurredAt;
        private String producer;
        private String traceId;
        private JsonNode payload;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder eventVersion(int eventVersion) {
            this.eventVersion = eventVersion;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder payload(JsonNode payload) {
            this.payload = payload;
            return this;
        }

        public Envelope build() {
            requireNotBlank(eventId, "eventId");
            requireNotBlank(eventType, "eventType");
            if (eventVersion <= 0) {
                throw new IllegalStateException("eventVersion 必须为正整数");
            }
            if (occurredAt == null) {
                throw new IllegalStateException("occurredAt 必填");
            }
            requireNotBlank(producer, "producer");
            if (payload == null) {
                throw new IllegalStateException("payload 必填");
            }
            return new Envelope(eventId, eventType, eventVersion, occurredAt, producer, traceId, payload);
        }

        private static void requireNotBlank(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(field + " 必填");
            }
        }
    }
}
