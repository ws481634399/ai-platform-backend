package com.ai.mall.order.application.compensation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** OrderAutoCancelCompensationHandler 单测（CHG-0025 STORY-009-05-01，TC-004）。 */
class OrderAutoCancelCompensationHandlerTest {

    private OrderCancelService orderCancelService;
    private OrderAutoCancelCompensationHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        orderCancelService = mock(OrderCancelService.class);
        handler = new OrderAutoCancelCompensationHandler(orderCancelService, objectMapper);
    }

    @Test
    void supports_仅自动取消操作() {
        org.junit.jupiter.api.Assertions.assertTrue(handler.supports(CompensationTask.OP_AUTO_CANCEL_ORDER));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports(CompensationTask.OP_RELEASE_INVENTORY));
    }

    @Test
    void handle_按载荷调systemCancel() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new OrderAutoCancelCompensationPayload(100L, "ORD-100", "evt-1"));
        CompensationTask task = CompensationTask.register(
                CompensationTask.TYPE_ORDER, "ORD-100", CompensationTask.OP_AUTO_CANCEL_ORDER,
                payload, "trace-1", Instant.now());

        handler.handle(task);

        verify(orderCancelService).systemCancel(100L, "PAYMENT_TIMEOUT", "COMPENSATION");
    }

    @Test
    void handle_状态冲突异常向上传播记退避() {
        String payload = "{\"orderId\":101,\"orderNo\":\"ORD-101\",\"eventId\":\"evt-2\"}";
        CompensationTask task = CompensationTask.reconstitute(
                2L, CompensationTask.TYPE_ORDER, "ORD-101", CompensationTask.OP_AUTO_CANCEL_ORDER,
                payload, CompensationStatus.PENDING, 1, CompensationTask.MAX_RETRIES, null,
                Instant.now().plusSeconds(60), "trace-2", Instant.now(), Instant.now());
        when(orderCancelService.systemCancel(101L, "PAYMENT_TIMEOUT", "COMPENSATION"))
                .thenThrow(new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT, "paid"));

        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void handle_坏载荷抛IllegalState() {
        CompensationTask task = CompensationTask.register(
                CompensationTask.TYPE_ORDER, "ORD-102", CompensationTask.OP_AUTO_CANCEL_ORDER,
                "not-json", "trace-3", Instant.now());

        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("补偿载荷解析失败");
    }
}
