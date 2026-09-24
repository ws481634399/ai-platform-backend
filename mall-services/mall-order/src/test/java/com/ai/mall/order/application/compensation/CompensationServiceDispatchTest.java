package com.ai.mall.order.application.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.order.domain.compensation.CompensationRepository;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * CompensationService 调度分派与 TraceId 贯通单测（CHG-0025 STORY-009-05-01，TC-005/TC-009）。
 */
class CompensationServiceDispatchTest {

    private final CompensationRepository repository = mock(CompensationRepository.class);
    private final CompensationActionHandler actionHandler = mock(CompensationActionHandler.class);

    private final CompensationService service = new CompensationService(
            repository, java.util.List.of(actionHandler), new ObjectMapper());

    @AfterEach
    void clearMdc() {
        MDC.remove(TraceConstants.MDC_KEY);
    }

    private CompensationTask task(String op, String traceId) {
        return CompensationTask.register(
                CompensationTask.TYPE_ORDER, "ORD-1", op, "{}", traceId, Instant.now());
    }

    @Test
    @DisplayName("成功路径：执行器处理后置 SUCCESS 并落库")
    void executeSuccess() {
        when(actionHandler.supports(CompensationTask.OP_AUTO_CANCEL_ORDER)).thenReturn(true);
        CompensationTask task = task(CompensationTask.OP_AUTO_CANCEL_ORDER, null);

        service.execute(task);

        verify(actionHandler).handle(task);
        assertThat(task.status()).isEqualTo(CompensationStatus.SUCCESS);
        verify(repository).update(task);
    }

    @Test
    @DisplayName("失败路径：执行器抛异常记退避，状态仍为 PENDING")
    void executeFailureBackoff() {
        when(actionHandler.supports(CompensationTask.OP_RELEASE_INVENTORY)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("down"))
                .when(actionHandler).handle(any());
        CompensationTask task = task(CompensationTask.OP_RELEASE_INVENTORY, null);

        service.execute(task);

        assertThat(task.status()).isEqualTo(CompensationStatus.PENDING);
        assertThat(task.retryCount()).isEqualTo(1);
        assertThat(task.nextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("TraceId 贯通：执行期间 MDC=任务 traceId，结束清理")
    void executePropagatesTraceId() {
        when(actionHandler.supports(CompensationTask.OP_AUTO_CANCEL_ORDER)).thenReturn(true);
        CompensationTask task = task(CompensationTask.OP_AUTO_CANCEL_ORDER, "trace-xyz");
        String[] seenDuringHandle = {null};
        doAnswer(invocation -> {
            seenDuringHandle[0] = MDC.get(TraceConstants.MDC_KEY);
            return null;
        }).when(actionHandler).handle(any());

        service.execute(task);

        assertThat(seenDuringHandle[0]).isEqualTo("trace-xyz");
        assertThat(MDC.get(TraceConstants.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("登记 ORDER_AUTO_CANCEL：payload 含 eventId，操作常量正确")
    void enqueueOrderAutoCancel() {
        service.enqueueOrderAutoCancel(88L, "ORD-88", "evt-88", "trace-88");

        org.mockito.ArgumentCaptor<CompensationTask> captor =
                org.mockito.ArgumentCaptor.forClass(CompensationTask.class);
        verify(repository).insertIgnore(captor.capture());
        CompensationTask saved = captor.getValue();
        assertThat(saved.operation()).isEqualTo(CompensationTask.OP_AUTO_CANCEL_ORDER);
        assertThat(saved.businessId()).isEqualTo("ORD-88");
        assertThat(saved.traceId()).isEqualTo("trace-88");
        assertThat(saved.payload()).contains("evt-88").contains("ORD-88");
    }
}
