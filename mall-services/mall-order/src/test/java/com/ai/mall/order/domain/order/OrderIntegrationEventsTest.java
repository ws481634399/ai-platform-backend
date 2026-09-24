package com.ai.mall.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 订单集成事件收集/拉取测试（TC-004）。
 */
class OrderIntegrationEventsTest {

    private static final ReceiverSnapshot RECEIVER = new ReceiverSnapshot(
            "张三", "13800000000", "浙江省", "杭州市", "西湖区", "文一路 1 号", null);

    private OrderItem item(String orderNo) {
        return new OrderItem(null, orderNo, 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
    }

    @Test
    void create_collectsOrderCreatedEvent() {
        Order order = Order.create("ON20250101001", 5001L, OrderSource.CART, Money.ofM4(2000L),
                RECEIVER, List.of(item("ON20250101001")), "tok-1", Instant.now());

        assertThat(order.pullIntegrationEvents())
                .extracting(event -> event.type())
                .containsExactly(OrderIntegrationEventType.ORDER_CREATED);
    }

    @Test
    void stateTransitions_collectEventsInOrder() {
        // 以 PAID 状态重建，再走取消分支验证事件顺序（pay/cancel 各分支独立断言亦可）
        Order paid = Order.reconstitute(9001L, "ON20250101002", 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item("ON20250101002")),
                null, null, null, "tok-2", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());

        paid.pay("5001", Instant.now());
        assertThat(paid.pullIntegrationEvents())
                .containsExactly(new com.ai.mall.order.domain.order.event.OrderIntegrationEvent(
                        OrderIntegrationEventType.PAYMENT_SUCCEEDED));

        Order pending = Order.reconstitute(9002L, "ON20250101003", 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item("ON20250101003")),
                null, null, null, "tok-3", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());
        pending.cancel("5001", "不想买了", Instant.now());
        assertThat(pending.pullIntegrationEvents())
                .containsExactly(new com.ai.mall.order.domain.order.event.OrderIntegrationEvent(
                        OrderIntegrationEventType.ORDER_CANCELLED));
    }

    @Test
    void pullIntegrationEvents_clearsAfterPull() {
        Order order = Order.create("ON20250101004", 5001L, OrderSource.CART, Money.ofM4(2000L),
                RECEIVER, List.of(item("ON20250101004")), "tok-4", Instant.now());

        assertThat(order.pullIntegrationEvents()).hasSize(1);
        assertThat(order.pullIntegrationEvents()).isEmpty();
    }

    @Test
    void reconstitute_hasNoPendingEvents() {
        Order order = Order.reconstitute(9003L, "ON20250101005", 5001L, OrderStatus.PAID,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item("ON20250101005")),
                null, null, null, "tok-5", Instant.now(), null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());

        assertThat(order.pullIntegrationEvents()).isEmpty();
    }

    @Test
    void confirmReceipt_collectsOrderCompletedEvent() {
        Order shipped = Order.reconstitute(9004L, "ON20250101006", 5001L, OrderStatus.SHIPPED,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item("ON20250101006")),
                "SF", "SF123", null, "tok-6", Instant.now(), null, Instant.now(), null, 0L,
                Instant.now(), Instant.now(), List.of());

        shipped.confirmReceipt("5001", Instant.now());
        assertThat(shipped.pullIntegrationEvents())
                .containsExactly(new com.ai.mall.order.domain.order.event.OrderIntegrationEvent(
                        OrderIntegrationEventType.ORDER_COMPLETED));
    }
}
