package com.ai.mall.order.application.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付服务异步/降级分支测试（TC-003）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

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
        return Order.reconstitute(9200L, orderNo, 5001L, OrderStatus.PENDING_PAYMENT,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item),
                null, null, null, "tok", null, null, null, null, 0L,
                Instant.now(), Instant.now(), List.of());
    }

    @Test
    void asyncMode_casWon_skipsSynchronousConfirm() {
        Order order = pendingOrder("ON5001");
        when(orderRepository.findByOrderNo("ON5001")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);

        Order result = new PaymentService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).pay(5001L, "ON5001");

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(inventoryPort, never()).confirm(anyString());
        verify(compensationPort, never()).enqueueInventoryConfirm(anyString(), anyList(), anyString());
    }

    @Test
    void syncMode_casWon_confirmsEachReservation() {
        Order order = pendingOrder("ON5002");
        when(orderRepository.findByOrderNo("ON5002")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);

        new PaymentService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(false)).pay(5001L, "ON5002");

        verify(inventoryPort, times(1)).confirm("ON5002:2001");
        verify(compensationPort, never()).enqueueInventoryConfirm(anyString(), anyList(), anyString());
    }

    @Test
    void syncMode_confirmFailed_enqueuesCompensation() {
        Order order = pendingOrder("ON5003");
        when(orderRepository.findByOrderNo("ON5003")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.transition(any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("inventory down"))
                .when(inventoryPort).confirm("ON5003:2001");

        new PaymentService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(false)).pay(5001L, "ON5003");

        verify(compensationPort, times(1)).enqueueInventoryConfirm(eq("ON5003"), anyList(), anyString());
    }

    @Test
    void duplicatePay_returnsOrderIdempotently() {
        OrderItem item = new OrderItem(null, "ON5004", 1001L, 2001L, "测试商品", "SKU1",
                Map.of("颜色", "红"), null, 1000L, 2);
        Order paid = Order.reconstitute(9201L, "ON5004", 5001L, OrderStatus.PAID,
                OrderSource.CART, Money.ofM4(2000L), RECEIVER, List.of(item),
                null, null, null, "tok", Instant.now(), null, null, null, 1L,
                Instant.now(), Instant.now(), List.of());
        when(orderRepository.findByOrderNo("ON5004")).thenReturn(java.util.Optional.of(paid));

        Order result = new PaymentService(orderRepository, inventoryPort, compensationPort,
                new IntegrationMode(true)).pay(5001L, "ON5004");

        assertThat(result).isSameAs(paid);
        verify(orderRepository, never()).transition(any());
        verify(inventoryPort, never()).confirm(anyString());
    }
}
