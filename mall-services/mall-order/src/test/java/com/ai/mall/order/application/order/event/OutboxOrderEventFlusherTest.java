package com.ai.mall.order.application.order.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
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

    private OrderEnvelopeAssembler realAssembler;

    private Order order;

    @BeforeEach
    void setUp() {
        realAssembler = new OrderEnvelopeAssembler(new ObjectMapper().findAndRegisterModules(), 30L);
        OrderItem item = new OrderItem(null, "ON4001", 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        order = Order.create("ON4001", 5001L, OrderSource.CART, Money.ofM4(2000L),
                RECEIVER, List.of(item), "tok", Instant.now());
        order.assignPersistedId(9100L);
    }

    @Test
    void asyncMode_appendsEnvelopeWithinTransaction() {
        Envelope envelope = realAssembler.assemble(order,
                new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_CREATED));
        org.mockito.Mockito.when(assembler.assemble(eq(order), any(OrderIntegrationEvent.class)))
                .thenReturn(envelope);

        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(true))
                .flush(order);

        verify(recordWriter, times(1)).append(eq("9100"), eq(EventTags.ORDER_CREATED), eq(envelope));
    }

    @Test
    void syncMode_dropsEventsWithoutAppend() {
        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(false))
                .flush(order);

        verify(recordWriter, never()).append(any(), any(), any());
        verify(assembler, never()).assemble(any(), any());
    }

    @Test
    void noEvents_noop() {
        order.pullIntegrationEvents();

        new OutboxOrderEventFlusher(assembler, recordWriter, new IntegrationMode(true))
                .flush(order);

        verify(recordWriter, never()).append(any(), any(), any());
    }
}
