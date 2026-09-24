package com.ai.mall.order.application.order.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.order.application.order.timeout.DelayLevelMapper;
import com.ai.mall.order.application.order.timeout.PaymentTimeoutPolicy;
import com.ai.mall.order.application.outbox.OutboxRecordWriter;
import com.ai.mall.order.domain.order.Money;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderSource;
import com.ai.mall.order.domain.order.ReceiverSnapshot;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Outbox 订单事件 Flusher 测试（TC-002）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxOrderEventFlusherTest {

    private static final ReceiverSnapshot RECEIVER = new ReceiverSnapshot(
            "张三", "13800000000", "浙江省", "杭州市", "西湖区", "文一路 1 号", null);

    @Mock
    private OrderEnvelopeAssembler assembler;
    @Mock
    private OutboxRecordWriter recordWriter;
    @Mock
    private PaymentTimeoutPolicy timeoutPolicy;

    private Order order;

    @BeforeEach
    void setUp() {
        OrderItem item = new OrderItem(null, "ON4001", 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        order = Order.create("ON4001", 5001L, OrderSource.CART, Money.ofM4(2000L),
                RECEIVER, List.of(item), "tok", Instant.now());
        order.assignPersistedId(9100L);
    }

    @Test
    void asyncMode_appendsEnvelopeWithMappedLevel() {
        Envelope createdEnv = Envelope.builder().eventId("e1").eventType(EventTags.ORDER_CREATED)
                .eventVersion(1).occurredAt(Instant.now()).producer("mall-order")
                .payload(new ObjectMapper().createObjectNode()).build();
        Envelope delayEnv = Envelope.builder().eventId("e2").eventType(EventTags.PAYMENT_TIMEOUT_CHECK)
                .eventVersion(1).occurredAt(Instant.now()).producer("mall-order")
                .payload(new ObjectMapper().createObjectNode()).build();
        when(timeoutPolicy.timeoutMinutes()).thenReturn(30L);
        when(assembler.assemble(eq(order), any(OrderIntegrationEvent.class), eq(30L)))
                .thenAnswer(invocation -> {
                    OrderIntegrationEventType type = invocation.getArgument(1, OrderIntegrationEvent.class).type();
                    return type == OrderIntegrationEventType.ORDER_CREATED ? createdEnv : delayEnv;
                });

        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(true),
                timeoutPolicy, new DelayLevelMapper(0)).flush(order);

        // ORDER_CREATED 即时 append；PAYMENT_TIMEOUT_CHECK 带映射级别 16（30m 恰好命中）
        verify(recordWriter, times(1)).append(eq("9100"), eq(EventTags.ORDER_CREATED), eq(createdEnv));
        verify(recordWriter, times(1))
                .append(eq("9100"), eq(EventTags.PAYMENT_TIMEOUT_CHECK), eq(delayEnv), eq(16));
        // 一次 flush 内超时只读取一次，两事件共用同一值
        verify(timeoutPolicy, times(1)).timeoutMinutes();
    }

    @Test
    void syncMode_dropsEventsWithoutAppend() {
        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(false),
                timeoutPolicy, new DelayLevelMapper(0)).flush(order);

        verify(recordWriter, never()).append(any(), any(), any());
        verify(assembler, never()).assemble(any(), any(), anyLong());
    }

    @Test
    void noEvents_noop() {
        order.pullIntegrationEvents();

        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(true),
                timeoutPolicy, new DelayLevelMapper(0)).flush(order);

        verify(recordWriter, never()).append(any(), any(), any());
        verify(timeoutPolicy, never()).timeoutMinutes();
    }
}
