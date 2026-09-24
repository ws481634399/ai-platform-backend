package com.ai.mall.order.application.order.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskAdminMapper;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 延迟任务管理编排服务单测（TC-007）。
 */
@ExtendWith(MockitoExtension.class)
class DelayTaskAdminServiceTest {

    @Mock
    private DelayTaskAdminMapper mapper;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCancelService orderCancelService;

    private DelayTaskAdminService service() {
        return new DelayTaskAdminService(mapper, orderRepository, orderCancelService);
    }

    private DelayTaskRow row(long orderId, String status) {
        DelayTaskRow row = new DelayTaskRow();
        row.setOrderId(orderId);
        row.setDelayStatus(status);
        return row;
    }

    @Test
    void page_normalizesStatusCaseAndPaging() {
        when(mapper.selectTasks(eq("PENDING"), eq(20), eq(10)))
                .thenReturn(List.of(row(1L, "PENDING")));
        when(mapper.countTasks("PENDING")).thenReturn(1L);

        DelayTaskAdminService.Page result = service().page("pending", 3, 10);

        org.assertj.core.api.Assertions.assertThat(result.records()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(result.total()).isEqualTo(1L);
    }

    @Test
    void page_invalidStatusIgnored_becomesNullFilter() {
        when(mapper.selectTasks(eq(null), eq(0), eq(10))).thenReturn(List.of());
        when(mapper.countTasks(null)).thenReturn(0L);

        service().page("WHAT", 1, 10);

        verify(mapper).selectTasks(null, 0, 10);
    }

    @Test
    void page_blankStatus_becomesNullFilter() {
        when(mapper.selectTasks(eq(null), eq(0), eq(5))).thenReturn(List.of());
        when(mapper.countTasks(null)).thenReturn(0L);

        service().page("  ", 1, 5);
    }

    @Test
    void cancel_blankReason_defaultsToAdminManual() {
        Instant now = Instant.now();
        when(orderRepository.findStatusById(7L)).thenReturn(Optional.of(OrderStatus.PENDING_PAYMENT));
        Order cancelled = org.mockito.Mockito.mock(Order.class);
        when(cancelled.getId()).thenReturn(7L);
        when(cancelled.orderNo()).thenReturn("ON7");
        when(cancelled.status()).thenReturn(OrderStatus.CANCELLED);
        when(cancelled.createdAt()).thenReturn(now);
        when(cancelled.cancelledAt()).thenReturn(now);
        when(orderCancelService.systemCancel(7L, "ADMIN_MANUAL", "ADMIN_MANUAL"))
                .thenReturn(cancelled);

        DelayTaskRow result = service().cancel(7L, null, "admin1");

        org.assertj.core.api.Assertions.assertThat(result.getDelayStatus()).isEqualTo("CANCELLED");
        org.assertj.core.api.Assertions.assertThat(result.getOrderNo()).isEqualTo("ON7");
    }

    @Test
    void cancel_customReason_trimmedAndPassed() {
        Order cancelled = org.mockito.Mockito.mock(Order.class);
        when(cancelled.getId()).thenReturn(8L);
        when(cancelled.orderNo()).thenReturn("ON8");
        when(cancelled.status()).thenReturn(OrderStatus.CANCELLED);
        when(cancelled.createdAt()).thenReturn(Instant.now());
        when(cancelled.cancelledAt()).thenReturn(Instant.now());
        when(orderRepository.findStatusById(8L)).thenReturn(Optional.of(OrderStatus.PENDING_PAYMENT));
        when(orderCancelService.systemCancel(8L, "运营介入", "ADMIN_MANUAL")).thenReturn(cancelled);

        service().cancel(8L, " 运营介入 ", "admin1");

        verify(orderCancelService).systemCancel(8L, "运营介入", "ADMIN_MANUAL");
    }

    @Test
    void cancel_orderNotFound_propagates() {
        when(orderRepository.findStatusById(404L)).thenReturn(Optional.empty());
        when(orderCancelService.systemCancel(eq(404L), any(), any()))
                .thenThrow(new RuntimeException("订单不存在"));

        assertThatThrownBy(() -> service().cancel(404L, null, "admin1"))
                .isInstanceOf(RuntimeException.class);
    }
}
