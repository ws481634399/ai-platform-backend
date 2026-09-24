package com.ai.mall.order.application.compensation;

import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.order.application.order.port.OrderCompensationPort.InventoryLine;
import com.ai.mall.order.domain.compensation.CompensationRepository;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * CompensationService 登记链路 traceId 单测（CHG-0023 TC-005，AC-012）：
 * MDC 有值 → 补偿任务携带登记时刻 traceId；MDC 缺失（非 HTTP 防御路径）→ null 且登记不失败。
 */
@ExtendWith(MockitoExtension.class)
class CompensationServiceEnqueueTraceTest {

    private static final String REQUEST_TRACE_ID = "fedcba9876543210fedcba9876543210";

    @Mock
    private CompensationRepository repository;
    @Mock
    private InventoryCompensationHandler inventoryHandler;

    private CompensationService service;

    @BeforeEach
    void setUp() {
        // @Mock 字段注入完成后再构造被测对象（字段内联初始化会早于 Mockito 注入）
        service = new CompensationService(repository, List.of(inventoryHandler), new ObjectMapper());
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(TraceConstants.MDC_KEY);
    }

    @Test
    @DisplayName("MDC 存在 traceId：登记的补偿任务携带该值")
    void enqueueCarriesMdcTraceId() {
        MDC.put(TraceConstants.MDC_KEY, REQUEST_TRACE_ID);
        List<InventoryLine> lines = List.of(new InventoryLine(6001L, 1, "order-no:6001"));

        service.enqueueInventoryRelease("order-no", lines, "锁后建单失败");

        ArgumentCaptor<CompensationTask> captor = ArgumentCaptor.forClass(CompensationTask.class);
        verify(repository).insertIgnore(captor.capture());
        assertThat(captor.getValue().traceId()).isEqualTo(REQUEST_TRACE_ID);
    }

    @Test
    @DisplayName("MDC 缺失/空白：traceId 归 null 且登记不抛异常（非 HTTP 路径容错）")
    void enqueueToleratesMissingMdc() {
        assertThat(MDC.get(TraceConstants.MDC_KEY)).isNull();
        List<InventoryLine> lines = List.of(new InventoryLine(6001L, 1, "order-no:6001"));

        service.enqueueInventoryRelease("order-no", lines, "非 HTTP 线程登记");

        ArgumentCaptor<CompensationTask> captor = ArgumentCaptor.forClass(CompensationTask.class);
        verify(repository).insertIgnore(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();

        // 空白字符串同样归 null
        MDC.put(TraceConstants.MDC_KEY, "   ");
        service.enqueueInventoryConfirm("order-no-2", lines, "空白 MDC");
        verify(repository, org.mockito.Mockito.times(2)).insertIgnore(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();
    }

    @Test
    @DisplayName("空行载荷：不登记（既有短路行为不变）")
    void emptyLinesSkipped() {
        MDC.put(TraceConstants.MDC_KEY, REQUEST_TRACE_ID);
        service.enqueueInventoryRelease("order-no", List.of(), "无行");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
