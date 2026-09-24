package com.ai.mall.order.interfaces.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单状态内部端点测试（TC-005）。
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusInternalControllerTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void status_found_returnsStatusView() {
        when(orderRepository.findStatusById(9400L)).thenReturn(Optional.of(OrderStatus.PAID));

        UnifyResult<OrderStatusInternalController.StatusView> result =
                new OrderStatusInternalController(orderRepository).status(9400L);

        assertThat(result.getData().orderId()).isEqualTo(9400L);
        assertThat(result.getData().status()).isEqualTo("PAID");
    }

    @Test
    void status_notFound_throwsNotFound() {
        when(orderRepository.findStatusById(9401L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new OrderStatusInternalController(orderRepository).status(9401L))
                .isInstanceOf(BusinessException.class);
    }
}
