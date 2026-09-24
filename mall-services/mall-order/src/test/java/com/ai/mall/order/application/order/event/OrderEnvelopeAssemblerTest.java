package com.ai.mall.order.application.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventVersions;
import com.ai.mall.order.domain.order.Money;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderSource;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.domain.order.ReceiverSnapshot;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 订单信封组装器测试（TC-001）。
 */
class OrderEnvelopeAssemblerTest {

    private static final ReceiverSnapshot RECEIVER = new ReceiverSnapshot(
            "张三", "13800000000", "浙江省", "杭州市", "西湖区", "文一路 1 号", null);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final String traceId = TraceContext.generate();

    @BeforeEach
    void setUp() {
        TraceContext.set(traceId);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private OrderItem item(String orderNo) {
        return new OrderItem(9001L, orderNo, 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
    }

    private Order createdOrder(String orderNo) {
        return Order.create(orderNo, 5001L, OrderSource.CART, Money.ofM4(2000L),
                RECEIVER, List.of(item(orderNo)), "tok", Instant.now());
    }

    @Test
    void assembleOrderCreated_envelopeWithDeadlinePayload() {
        String orderNo = "ON3001";
        Order order = createdOrder(orderNo);
        order.assignPersistedId(9001L);

        Envelope envelope = new OrderEnvelopeAssembler(objectMapper)
                .assemble(order, new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_CREATED), 30L);

        assertThat(envelope.getEventType()).isEqualTo(EventTags.ORDER_CREATED);
        assertThat(envelope.getEventVersion()).isEqualTo(EventVersions.CURRENT);
        assertThat(envelope.getProducer()).isEqualTo("mall-order");
        assertThat(envelope.getTraceId()).isEqualTo(traceId);
        assertThat(envelope.getEventId()).isNotBlank();
        JsonNode payload = envelope.getPayload();
        assertThat(payload.get("orderId").asText()).isEqualTo("9001");
        assertThat(payload.get("orderNo").asText()).isEqualTo(orderNo);
        assertThat(payload.get("reservationNo").asText()).isEqualTo(orderNo);
        assertThat(payload.get("orderAmount").asLong()).isEqualTo(2000L);
        assertThat(payload.get("currency").asText()).isEqualTo("CNY");
        long expectedDeadline = order.createdAt().plus(30, ChronoUnit.MINUTES).getEpochSecond();
        assertThat((long) payload.get("paymentDeadline").asDouble())
                .isEqualTo(expectedDeadline);
    }

    @Test
    void assemblePaymentSucceeded_payNoAndPaidAtPayload() {
        String orderNo = "ON3002";
        Instant paidAt = Instant.now();
        Order order = Order.reconstitute(9002L, orderNo, 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item(orderNo)),
                null, null, null, "tok", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());
        order.pay("5001", paidAt);

        Envelope envelope = new OrderEnvelopeAssembler(objectMapper)
                .assemble(order, new OrderIntegrationEvent(OrderIntegrationEventType.PAYMENT_SUCCEEDED), 30L);

        assertThat(envelope.getEventType()).isEqualTo(EventTags.PAYMENT_SUCCEEDED);
        JsonNode payload = envelope.getPayload();
        assertThat(payload.get("paymentNo").asText()).isEqualTo("PAY" + orderNo);
        assertThat(payload.get("reservationNo").asText()).isEqualTo(orderNo);
        assertThat(payload.get("paymentAmount").asLong()).isEqualTo(2000L);
        assertThat(payload.get("currency").asText()).isEqualTo("CNY");
        assertThat((long) payload.get("paidAt").asDouble())
                .isEqualTo(paidAt.getEpochSecond());
    }

    @Test
    void assembleOrderCancelled_reasonPayload() {
        String orderNo = "ON3003";
        Instant cancelledAt = Instant.now();
        Order order = Order.reconstitute(9003L, orderNo, 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item(orderNo)),
                null, null, null, "tok", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());
        order.cancel("5001", "不想买了", cancelledAt);

        Envelope envelope = new OrderEnvelopeAssembler(objectMapper)
                .assemble(order, new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_CANCELLED), 30L);

        JsonNode payload = envelope.getPayload();
        assertThat(payload.get("reservationNo").asText()).isEqualTo(orderNo);
        assertThat(payload.get("cancelReason").asText()).isEqualTo("不想买了");
        assertThat((long) payload.get("cancelledAt").asDouble())
                .isEqualTo(cancelledAt.getEpochSecond());
    }

    @Test
    void assembleOrderCompleted_completedAtPayload() {
        String orderNo = "ON3004";
        Instant now = Instant.now();
        Order order = Order.reconstitute(9004L, orderNo, 5001L, OrderStatus.SHIPPED,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item(orderNo)),
                "SF", "SF1", null, "tok", now.minusSeconds(60), null, now.minusSeconds(30), null, 2L,
                now.minusSeconds(120), now, List.of());
        order.confirmReceipt("5001", now);

        Envelope envelope = new OrderEnvelopeAssembler(objectMapper)
                .assemble(order, new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_COMPLETED), 30L);

        JsonNode payload = envelope.getPayload();
        assertThat(payload.get("orderId").asText()).isEqualTo("9004");
        assertThat((long) payload.get("completedAt").asDouble())
                .isEqualTo(now.getEpochSecond());
    }

    @Test
    void assemblePaymentTimeoutCheck_delayPayload() {
        String orderNo = "ON3005";
        Order order = createdOrder(orderNo);
        order.assignPersistedId(9005L);

        Envelope envelope = new OrderEnvelopeAssembler(objectMapper)
                .assemble(order, new OrderIntegrationEvent(OrderIntegrationEventType.PAYMENT_TIMEOUT_CHECK), 15L);

        assertThat(envelope.getEventType()).isEqualTo(EventTags.PAYMENT_TIMEOUT_CHECK);
        JsonNode payload = envelope.getPayload();
        assertThat(payload.get("orderId").asText()).isEqualTo("9005");
        assertThat(payload.get("orderNo").asText()).isEqualTo(orderNo);
        long expectedExpire = order.createdAt().plus(15, ChronoUnit.MINUTES).getEpochSecond();
        assertThat((long) payload.get("expireAt").asDouble())
                .isEqualTo(expectedExpire);
    }
}
