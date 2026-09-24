package com.ai.mall.order.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.event.IntegrationMode;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import com.ai.mall.order.domain.order.Money;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderSource;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.domain.order.ReceiverSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 会员取消服务异步/降级分支测试（TC-003）。
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    private static final ReceiverSnapshot RECEIVER = new ReceiverSnapshot(
            "张三", "13800000000", "浙江省", "杭州市", "西湖区", "文一路 1 号", null);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryPort inventoryPort;
    @Mock
    private OrderCompensationPort compensationPort;

    private Order pendingOrder(String orderNo) {
        OrderItem item = new OrderItem(null, orderNo, 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        return Order.reconstitute(9300L, orderNo, 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item),
                null, null, null, "tok", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());
    }

    @Test
    void asyncMode_casWon_skipsSynchronousRelease() {
        Order order = pendingOrder("ON6001");
        when(orderRepository.findByOrderNo("ON6001")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);

        Order result = new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).cancel(5001L, "ON6001", "不想买了");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(anyString());
        verify(compensationPort, never()).enqueueInventoryRelease(anyString(), anyList(), anyString());
    }

    @Test
    void syncMode_casWon_releasesEachReservation() {
        Order order = pendingOrder("ON6002");
        when(orderRepository.findByOrderNo("ON6002")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);

        new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(false)).cancel(5001L, "ON6002", null);

        verify(inventoryPort, times(1)).release("ON6002:2001");
        verify(compensationPort, never()).enqueueInventoryRelease(anyString(), anyList(), anyString());
    }

    @Test
    void syncMode_releaseFailed_enqueuesCompensation() {
        Order order = pendingOrder("ON6003");
        when(orderRepository.findByOrderNo("ON6003")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("inventory down"))
                .when(inventoryPort).release("ON6003:2001");

        new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(false)).cancel(5001L, "ON6003", null);

        verify(compensationPort, times(1)).enqueueInventoryRelease(eq("ON6003"), anyList(), anyString());
    }

    @Test
    void duplicateCancel_returnsOrderIdempotently() {
        OrderItem item = new OrderItem(null, "ON6004", 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        Order cancelled = Order.reconstitute(9301L, "ON6004", 5001L, OrderStatus.CANCELLED,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item),
                null, null, "已取消", "tok", null, Instant.now(), null, null, 1L,
                Instant.now(), Instant.now(), List.of());
        when(orderRepository.findByOrderNo("ON6004")).thenReturn(java.util.Optional.of(cancelled));

        Order result = new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).cancel(5001L, "ON6004", null);

        assertThat(result).isSameAs(cancelled);
        verify(orderRepository, never()).transition(any());
        verify(inventoryPort, never()).release(anyString());
    }

    @Test
    void systemCancel_loadsByIdWithoutOwnership_operatorMarkedSystem() {
        Order order = pendingOrder("ON6005");
        when(orderRepository.findById(9300L)).thenReturn(Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);

        Order result = new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).systemCancel(9300L, "PAYMENT_TIMEOUT", "DELAY_MESSAGE");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        org.mockito.ArgumentCaptor<OrderRepository.StatusTransition> captor =
                org.mockito.ArgumentCaptor.forClass(OrderRepository.StatusTransition.class);
        verify(orderRepository).transition(captor.capture());
        assertThat(captor.getValue().operator()).isEqualTo("SYS:DELAY_MESSAGE");
        assertThat(captor.getValue().reason()).isEqualTo("PAYMENT_TIMEOUT");
        verify(inventoryPort, never()).release(anyString());
    }

    @Test
    void systemCancel_notFound_throws404() {
        when(orderRepository.findById(9404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).systemCancel(9404L, "PAYMENT_TIMEOUT", "DELAY_MESSAGE"))
                .isInstanceOf(BusinessException.class);
        verify(orderRepository, never()).transition(any());
    }

    @Test
    void systemCancel_casLostToPayment_throwsConflict() {
        Order order = pendingOrder("ON6006");
        OrderItem item = new OrderItem(null, "ON6006", 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        Order paid = Order.reconstitute(9300L, "ON6006", 5001L, OrderStatus.PAID,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item),
                null, null, null, "tok", null, null, Instant.now(), null, 1L,
                Instant.now(), Instant.now(), List.of());
        when(orderRepository.findById(9300L)).thenReturn(Optional.of(order), Optional.of(paid));
        when(orderRepository.transition(any())).thenReturn(false);

        assertThatThrownBy(() -> new OrderCancelService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).systemCancel(9300L, "PAYMENT_TIMEOUT", "DELAY_MESSAGE"))
                .isInstanceOf(BusinessException.class);
        verify(inventoryPort, never()).release(anyString());
    }
}
