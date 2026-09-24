package com.ai.mall.inventory.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.event.Envelope;
import com.ai.mall.event.payload.PaymentSucceededEventPayload;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.domain.inventory.InventoryRepository;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient.CompensationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PAYMENT_SUCCEEDED 库存消费者测试（TC-006）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentSucceededInventoryHandlerTest {

    private static final String ORDER_NO = "ON8001";
    private static final String ORDER_ID = "9600";
    private static final String RID = ORDER_NO + ":2001";

    @Mock
    private InventoryApplicationService inventoryApplicationService;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private com.ai.mall.common.mq.consumer.IdempotentConsumer idempotentConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PaymentSucceededInventoryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentSucceededInventoryHandler(objectMapper, idempotentConsumer,
                inventoryApplicationService, inventoryRepository, orderServiceClient);
    }

    private Envelope envelope(PaymentSucceededEventPayload payload) {
        return Envelope.builder()
                .eventId("evt-1").eventType("PAYMENT_SUCCEEDED").eventVersion(1)
                .occurredAt(Instant.now()).producer("mall-order").traceId(null)
                .payload(objectMapper.valueToTree(payload))
                .build();
    }

    private PaymentSucceededEventPayload payload() {
        return new PaymentSucceededEventPayload(ORDER_ID, ORDER_NO, "PAY" + ORDER_NO, ORDER_NO,
                2000L, "CNY", Instant.now());
    }

    @Test
    void orderStillActive_confirmsEachReservation() throws Exception {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("PAID");
        when(inventoryRepository.findReservationIdsByOrderNo(ORDER_NO)).thenReturn(List.of(RID));

        handler.handle(envelope(payload()), payload());

        verify(inventoryApplicationService, times(1)).confirmDeduction(any());
        verify(orderServiceClient, never()).registerCompensation(any());
    }

    @Test
    void orderCancelled_skipsWithoutConfirm() throws Exception {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("CANCELLED");

        handler.handle(envelope(payload()), payload());

        verify(inventoryApplicationService, never()).confirmDeduction(any());
        verify(inventoryRepository, never()).findReservationIdsByOrderNo(any());
    }

    @Test
    void confirmFailed_registersCompensationAndRethrows() {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("PAID");
        when(inventoryRepository.findReservationIdsByOrderNo(ORDER_NO)).thenReturn(List.of(RID));
        when(inventoryRepository.findReservationByReservationId(RID))
                .thenReturn(java.util.Optional.of(new InventoryReservation(RID, 2001L, 2)));
        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
                .when(inventoryApplicationService).confirmDeduction(any());

        assertThatThrownBy(() -> handler.handle(envelope(payload()), payload()))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<CompensationRequest> captor = ArgumentCaptor.forClass(CompensationRequest.class);
        verify(orderServiceClient, times(1)).registerCompensation(captor.capture());
        CompensationRequest registered = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(registered.type()).isEqualTo("INVENTORY_CONFIRM_DEDUCT");
        org.assertj.core.api.Assertions.assertThat(registered.lines()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(registered.lines().get(0).skuId()).isEqualTo(2001L);
    }

    @Test
    void statusQueryTransportError_propagatesForRetry() {
        when(orderServiceClient.getStatus(ORDER_ID)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> handler.handle(envelope(payload()), payload()))
                .isInstanceOf(RuntimeException.class);

        verify(inventoryApplicationService, never()).confirmDeduction(any());
    }
}
