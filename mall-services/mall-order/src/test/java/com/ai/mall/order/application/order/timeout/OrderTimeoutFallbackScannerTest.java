package com.ai.mall.order.application.order.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderRepository.ExpiredOrder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 超时兜底扫描器测试（TC-005）。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutFallbackScannerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCancelService orderCancelService;
    @Mock
    private PaymentTimeoutPolicy timeoutPolicy;

    @Test
    void scan_cancelsEveryExpiredPending() {
        when(timeoutPolicy.timeoutMinutes()).thenReturn(30L);
        when(orderRepository.findExpiredPending(any(), eq(100)))
                .thenReturn(List.of(new ExpiredOrder(1L, "ON1"), new ExpiredOrder(2L, "ON2")));

        new OrderTimeoutFallbackScanner(orderRepository, orderCancelService, timeoutPolicy, 100).scan();

        verify(orderCancelService, times(1))
                .systemCancel(1L, "PAYMENT_TIMEOUT", "TIMEOUT_FALLBACK");
        verify(orderCancelService, times(1))
                .systemCancel(2L, "PAYMENT_TIMEOUT", "TIMEOUT_FALLBACK");
    }

    @Test
    void scan_singleFailureIsolated_othersContinue() {
        when(timeoutPolicy.timeoutMinutes()).thenReturn(30L);
        when(orderRepository.findExpiredPending(any(), eq(100)))
                .thenReturn(List.of(new ExpiredOrder(1L, "ON1"), new ExpiredOrder(2L, "ON2")));
        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
                .when(orderCancelService).systemCancel(1L, "PAYMENT_TIMEOUT", "TIMEOUT_FALLBACK");

        new OrderTimeoutFallbackScanner(orderRepository, orderCancelService, timeoutPolicy, 100).scan();

        verify(orderCancelService, times(1))
                .systemCancel(2L, "PAYMENT_TIMEOUT", "TIMEOUT_FALLBACK");
    }

    @Test
    void scan_cutoffIsNowMinusPolicyTimeout() {
        when(timeoutPolicy.timeoutMinutes()).thenReturn(30L);
        when(orderRepository.findExpiredPending(any(), eq(100))).thenReturn(List.of());

        new OrderTimeoutFallbackScanner(orderRepository, orderCancelService, timeoutPolicy, 100).scan();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findExpiredPending(captor.capture(), eq(100));
        Instant expected = Instant.now().minus(30, ChronoUnit.MINUTES);
        // 允许 5 秒调度/执行偏差
        assertThat(Math.abs(captor.getValue().getEpochSecond() - expected.getEpochSecond())).isLessThan(5);
    }
}
