package com.ai.mall.order.application.order.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.payload.OrderDelayPayload;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.application.compensation.CompensationService;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * PAYMENT_TIMEOUT_CHECK 到期回查消费者测试（TC-001~004）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentTimeoutCheckHandlerTest {

    private static final long ORDER_ID = 9800L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCancelService orderCancelService;
    @Mock
    private CompensationService compensationService;
    @Mock
    private IdempotentConsumer idempotentConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PaymentTimeoutCheckHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentTimeoutCheckHandler(objectMapper, idempotentConsumer,
                orderRepository, orderCancelService, compensationService);
    }

    private Envelope envelope() {
        OrderDelayPayload payload = new OrderDelayPayload(
                String.valueOf(ORDER_ID), "ON9800", Instant.now().plusSeconds(1800));
        return Envelope.builder()
                .eventId("evt-delay-1").eventType("PAYMENT_TIMEOUT_CHECK").eventVersion(1)
                .occurredAt(Instant.now()).producer("mall-order").traceId(null)
                .payload(objectMapper.valueToTree(payload))
                .build();
    }

    @Test
    void stillPending_systemCancelsViaM4Flow() throws Exception {
        when(orderRepository.findStatusById(ORDER_ID))
                .thenReturn(Optional.of(OrderStatus.PENDING_PAYMENT));

        handler.handle(envelope(),
                new OrderDelayPayload(String.valueOf(ORDER_ID), "ON9800", Instant.now()));

        verify(orderCancelService, times(1))
                .systemCancel(ORDER_ID, "PAYMENT_TIMEOUT", "DELAY_MESSAGE");
    }

    @Test
    void alreadyPaid_skipsCancel() throws Exception {
        when(orderRepository.findStatusById(ORDER_ID)).thenReturn(Optional.of(OrderStatus.PAID));

        handler.handle(envelope(),
                new OrderDelayPayload(String.valueOf(ORDER_ID), "ON9800", Instant.now()));

        verify(orderCancelService, never()).systemCancel(anyLong(), any(), any());
        verify(idempotentConsumer, times(1)).markResult(
                eq("evt-delay-1"), eq("order-delay-consumer-group"),
                eq(IdempotentConsumer.Result.SKIPPED));
    }

    @Test
    void orderVanished_skipsCancel() throws Exception {
        when(orderRepository.findStatusById(ORDER_ID)).thenReturn(Optional.empty());

        handler.handle(envelope(),
                new OrderDelayPayload(String.valueOf(ORDER_ID), "ON9800", Instant.now()));

        verify(orderCancelService, never()).systemCancel(anyLong(), any(), any());
        verify(idempotentConsumer, times(1)).markResult(
                eq("evt-delay-1"), eq("order-delay-consumer-group"),
                eq(IdempotentConsumer.Result.SKIPPED));
    }

    @Test
    void cancelLosesToPayment_statusConflictMergedAsSkipped() throws Exception {
        when(orderRepository.findStatusById(ORDER_ID))
                .thenReturn(Optional.of(OrderStatus.PENDING_PAYMENT));
        when(orderCancelService.systemCancel(ORDER_ID, "PAYMENT_TIMEOUT", "DELAY_MESSAGE"))
                .thenThrow(new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                        "订单状态已变化"));

        handler.handle(envelope(),
                new OrderDelayPayload(String.valueOf(ORDER_ID), "ON9800", Instant.now()));

        verify(idempotentConsumer, times(1)).markResult(
                eq("evt-delay-1"), eq("order-delay-consumer-group"),
                eq(IdempotentConsumer.Result.SKIPPED));
    }

    @Test
    void otherError_registersCompensationAndPropagatesForRetry() {
        when(orderRepository.findStatusById(ORDER_ID))
                .thenReturn(Optional.of(OrderStatus.PENDING_PAYMENT));
        when(orderCancelService.systemCancel(ORDER_ID, "PAYMENT_TIMEOUT", "DELAY_MESSAGE"))
                .thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> handler.handle(envelope(),
                new OrderDelayPayload(String.valueOf(ORDER_ID), "ON9800", Instant.now())))
                .isInstanceOf(RuntimeException.class);

        verify(idempotentConsumer, never()).markResult(any(), any(), any());
        // AC-035：真正取消失败先登记 ORDER_AUTO_CANCEL 补偿，再重抛交 MQ 重试
        verify(compensationService, times(1)).enqueueOrderAutoCancel(
                ORDER_ID, "ON9800", "evt-delay-1", null);
    }
}
