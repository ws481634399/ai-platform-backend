package com.ai.mall.inventory.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.payload.OrderCancelledEventPayload;
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
 * ORDER_CANCELLED 库存消费者测试（TC-007）。
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelledInventoryHandlerTest {

    private static final String ORDER_NO = "ON9001";
    private static final String ORDER_ID = "9700";
    private static final String RID = ORDER_NO + ":2001";

    @Mock
    private InventoryApplicationService inventoryApplicationService;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private IdempotentConsumer idempotentConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderCancelledInventoryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderCancelledInventoryHandler(objectMapper, idempotentConsumer,
                inventoryApplicationService, inventoryRepository, orderServiceClient);
    }

    private Envelope envelope(OrderCancelledEventPayload payload) {
        return Envelope.builder()
                .eventId("evt-2").eventType("ORDER_CANCELLED").eventVersion(1)
                .occurredAt(Instant.now()).producer("mall-order").traceId(null)
                .payload(objectMapper.valueToTree(payload))
                .build();
    }

    private OrderCancelledEventPayload payload() {
        return new OrderCancelledEventPayload(ORDER_ID, ORDER_NO, ORDER_NO,
                "USER_CANCELLED", Instant.now());
    }

    @Test
    void orderCancelled_releasesEachReservation() throws Exception {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("CANCELLED");
        when(inventoryRepository.findReservationIdsByOrderNo(ORDER_NO)).thenReturn(List.of(RID));

        handler.handle(envelope(payload()), payload());

        verify(inventoryApplicationService, times(1)).release(any());
        verify(orderServiceClient, never()).registerCompensation(any());
    }

    @Test
    void orderAlreadyPaid_skipsRelease() throws Exception {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("PAID");

        handler.handle(envelope(payload()), payload());

        verify(inventoryApplicationService, never()).release(any());
        verify(inventoryRepository, never()).findReservationIdsByOrderNo(any());
    }

    @Test
    void orderCompleted_skipsRelease() throws Exception {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("COMPLETED");

        handler.handle(envelope(payload()), payload());

        verify(inventoryApplicationService, never()).release(any());
    }

    @Test
    void releaseFailed_registersCompensationAndRethrows() {
        when(orderServiceClient.getStatus(ORDER_ID)).thenReturn("CANCELLED");
        when(inventoryRepository.findReservationIdsByOrderNo(ORDER_NO)).thenReturn(List.of(RID));
        when(inventoryRepository.findReservationByReservationId(RID))
                .thenReturn(java.util.Optional.of(new InventoryReservation(RID, 2001L, 2)));
        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
                .when(inventoryApplicationService).release(any());

        assertThatThrownBy(() -> handler.handle(envelope(payload()), payload()))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<CompensationRequest> captor = ArgumentCaptor.forClass(CompensationRequest.class);
        verify(orderServiceClient, times(1)).registerCompensation(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("INVENTORY_RELEASE");
        assertThat(captor.getValue().lines().get(0).reservationId()).isEqualTo(RID);
    }

    @Test
    void statusQueryTransportError_propagatesForRetry() {
        when(orderServiceClient.getStatus(ORDER_ID)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> handler.handle(envelope(payload()), payload()))
                .isInstanceOf(RuntimeException.class);

        verify(inventoryApplicationService, never()).release(any());
    }
}
