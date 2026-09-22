package com.ai.mall.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.event.payload.OrderCancelledEventPayload;
import com.ai.mall.event.payload.OrderCompletedEventPayload;
import com.ai.mall.event.payload.OrderCreatedEventPayload;
import com.ai.mall.event.payload.OrderDelayPayload;
import com.ai.mall.event.payload.PaymentSucceededEventPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件契约快照测试（CHG-0025 STORY-009-01-01 TC-002/TC-003）。
 * <p>锁定 Envelope 七字段、Topic/Tag/消费者组/版本常量与 §41 Payload 字段集，
 * 防止契约字段被静默增删（破坏跨服务兼容）。</p>
 */
class EventContractsSnapshotTest {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static Envelope fullEnvelope() {
        return Envelope.builder()
                .eventId("5f0c9a2e-1f3b-4a5d-8c7e-9b0a1d2e3f4a")
                .eventType(EventTags.ORDER_CREATED)
                .eventVersion(EventVersions.CURRENT)
                .occurredAt(Instant.parse("2026-08-01T06:30:00Z"))
                .producer("mall-order")
                .traceId("0123456789abcdef0123456789abcdef")
                .payload(mapper.createObjectNode().put("orderId", "50001"))
                .build();
    }

    @Test
    @DisplayName("TC-002：Envelope 七字段齐全非空，build 缺必填字段拒绝")
    void envelopeSevenFieldsMustBePresent() {
        Envelope envelope = fullEnvelope();
        assertThat(envelope.getEventId()).isNotBlank();
        assertThat(envelope.getEventType()).isNotBlank();
        assertThat(envelope.getEventVersion()).isEqualTo(1);
        assertThat(envelope.getOccurredAt()).isNotNull();
        assertThat(envelope.getProducer()).isNotBlank();
        // traceId 允许为空：发送侧从 MDC 读取，缺失时由 mall-mq 生产者自动生成
        assertThat(envelope.getTraceId()).isNotBlank();
        assertThat(envelope.getPayload()).isNotNull();

        List.of("eventId", "eventType", "eventVersion", "occurredAt",
                "producer", "traceId", "payload").forEach(field ->
                assertThat(envelope).hasFieldOrPropertyWithValue(field, envelopeFieldValue(envelope, field)));
    }

    private Object envelopeFieldValue(Envelope envelope, String field) {
        return switch (field) {
            case "eventId" -> envelope.getEventId();
            case "eventType" -> envelope.getEventType();
            case "eventVersion" -> envelope.getEventVersion();
            case "occurredAt" -> envelope.getOccurredAt();
            case "producer" -> envelope.getProducer();
            case "traceId" -> envelope.getTraceId();
            case "payload" -> envelope.getPayload();
            default -> throw new IllegalArgumentException(field);
        };
    }

    @Test
    @DisplayName("TC-002：Envelope build 工厂必填校验——缺 eventId 抛异常")
    void envelopeBuilderRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> Envelope.builder()
                .eventType(EventTags.ORDER_CREATED)
                .eventVersion(1)
                .occurredAt(Instant.now())
                .producer("mall-order")
                .payload(mapper.createObjectNode())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("TC-002：Envelope Jackson 序列化往返保持字段与 payload 原样")
    void envelopeJacksonRoundTrip() throws Exception {
        Envelope envelope = fullEnvelope();
        String json = mapper.writeValueAsString(envelope);
        Envelope parsed = mapper.readValue(json, Envelope.class);

        assertThat(parsed.getEventId()).isEqualTo(envelope.getEventId());
        assertThat(parsed.getEventType()).isEqualTo(envelope.getEventType());
        assertThat(parsed.getEventVersion()).isEqualTo(envelope.getEventVersion());
        assertThat(parsed.getOccurredAt()).isEqualTo(envelope.getOccurredAt());
        assertThat(parsed.getProducer()).isEqualTo(envelope.getProducer());
        assertThat(parsed.getTraceId()).isEqualTo(envelope.getTraceId());
        assertThat(parsed.getPayload().get("orderId").asText()).isEqualTo("50001");
    }

    @Test
    @DisplayName("TC-003：Topic/Tag/消费者组/版本常量快照（禁止业务侧散落硬编码的单一事实源）")
    void constantsSnapshot() {
        assertThat(EventTopics.AIMALL_ORDER_EVENTS).isEqualTo("aimall-order-events");
        assertThat(EventTopics.AIMALL_ORDER_DELAY).isEqualTo("aimall-order-delay");
        assertThat(EventTopics.AIMALL_INVENTORY_EVENTS).isEqualTo("aimall-inventory-events");

        assertThat(EventTags.ORDER_CREATED).isEqualTo("ORDER_CREATED");
        assertThat(EventTags.PAYMENT_SUCCEEDED).isEqualTo("PAYMENT_SUCCEEDED");
        assertThat(EventTags.ORDER_CANCELLED).isEqualTo("ORDER_CANCELLED");
        assertThat(EventTags.ORDER_COMPLETED).isEqualTo("ORDER_COMPLETED");
        assertThat(EventTags.PAYMENT_TIMEOUT_CHECK).isEqualTo("PAYMENT_TIMEOUT_CHECK");

        assertThat(ConsumerGroups.INVENTORY_CONSUMER_GROUP).isEqualTo("inventory-consumer-group");
        assertThat(ConsumerGroups.ORDER_DELAY_CONSUMER_GROUP).isEqualTo("order-delay-consumer-group");

        assertThat(EventVersions.CURRENT).isEqualTo(1);
        assertThat(EventVersions.MAX_SUPPORTED).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-003：§41 四事件 Payload 字段名快照对齐 10-API与事件契约.md §41")
    void payloadFieldNamesAlignedWithSection41() throws Exception {
        Map<Class<?>, List<String>> expected = Map.of(
                OrderCreatedEventPayload.class,
                List.of("orderId", "orderNo", "memberId", "reservationNo",
                        "orderAmount", "currency", "paymentDeadline"),
                PaymentSucceededEventPayload.class,
                List.of("orderId", "orderNo", "paymentNo", "reservationNo",
                        "paymentAmount", "currency", "paidAt"),
                OrderCancelledEventPayload.class,
                List.of("orderId", "orderNo", "reservationNo", "cancelReason", "cancelledAt"),
                OrderCompletedEventPayload.class,
                List.of("orderId", "orderNo", "memberId", "completedAt"),
                OrderDelayPayload.class,
                List.of("orderId", "orderNo", "expireAt"));

        expected.forEach((type, fields) -> {
            JsonNode node = mapper.valueToTree(sampleOf(type));
            assertThat(node.fieldNames())
                    .as("%s 字段集必须与 §41 契约一致", type.getSimpleName())
                    .toIterable()
                    .containsExactlyInAnyOrderElementsOf(fields);
        });
    }

    private Object sampleOf(Class<?> type) {
        if (type == OrderCreatedEventPayload.class) {
            return new OrderCreatedEventPayload("50001", "ORD202608010001", "10001",
                    "RES202608010001", 599800L, "CNY", Instant.parse("2026-08-01T06:30:00Z"));
        }
        if (type == PaymentSucceededEventPayload.class) {
            return new PaymentSucceededEventPayload("50001", "ORD202608010001", "PAY202608010001",
                    "RES202608010001", 599800L, "CNY", Instant.parse("2026-08-01T06:00:00Z"));
        }
        if (type == OrderCancelledEventPayload.class) {
            return new OrderCancelledEventPayload("50001", "ORD202608010001",
                    "RES202608010001", "PAYMENT_TIMEOUT", Instant.parse("2026-08-01T06:31:00Z"));
        }
        if (type == OrderCompletedEventPayload.class) {
            return new OrderCompletedEventPayload("50001", "ORD202608010001",
                    "10001", Instant.parse("2026-08-05T04:00:00Z"));
        }
        return new OrderDelayPayload("50001", "ORD202608010001",
                Instant.parse("2026-08-01T07:00:00Z"));
    }
}
