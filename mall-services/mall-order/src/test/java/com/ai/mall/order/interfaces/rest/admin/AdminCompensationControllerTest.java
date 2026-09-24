package com.ai.mall.order.interfaces.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.compensation.CompensationService;
import com.ai.mall.order.domain.compensation.CompensationRepository.CompensationPage;
import com.ai.mall.order.interfaces.rest.admin.dto.AdminOrderDtos;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AdminCompensationController 单测（CHG-0025 STORY-009-05-01，TC-006/007）。
 */
class AdminCompensationControllerTest {

    private CompensationService compensationService;
    private AdminCompensationController controller;

    @BeforeEach
    void setUp() {
        compensationService = mock(CompensationService.class);
        controller = new AdminCompensationController(compensationService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-zhao", "n/a", List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private CompensationTask task(long id) {
        return CompensationTask.reconstitute(
                id, CompensationTask.TYPE_ORDER, "ORD-" + id, CompensationTask.OP_RELEASE_INVENTORY,
                "{\"orderId\":1}", CompensationStatus.PENDING, 0, CompensationTask.MAX_RETRIES,
                null, Instant.now(), "trace-1", Instant.now(), Instant.now());
    }

    @Test
    void page_透传白名单操作与聚合筛选() {
        CompensationPage pageResult = new CompensationPage(List.of(task(7L)), 1L, 1, 10);
        when(compensationService.page(
                org.mockito.ArgumentMatchers.eq(CompensationTask.OP_RELEASE_INVENTORY),
                org.mockito.ArgumentMatchers.eq("ORD"),
                org.mockito.ArgumentMatchers.eq("PENDING"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(pageResult);

        UnifyResult<OrderDtos.PageView<AdminOrderDtos.CompensationView>> response = controller.page(
                CompensationTask.OP_RELEASE_INVENTORY, "ORD", "PENDING", 1, 10);

        assertThat(response.getData().records()).hasSize(1);
        // payload 原文透传（AC-037 查看载荷）
        assertThat(response.getData().records().get(0).payload()).isEqualTo("{\"orderId\":1}");
        verify(compensationService).page(
                CompensationTask.OP_RELEASE_INVENTORY, "ORD", "PENDING", 1, 10);
    }

    @Test
    void page_非法操作转null() {
        when(compensationService.page(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new CompensationPage(List.of(), 0, 1, 10));

        controller.page("DROP TABLE", "ORD", null, 1, 10);

        verify(compensationService).page(null, "ORD", null, 1, 10);
    }

    @Test
    void complete_调manualComplete() {
        CompensationTask succeeded = CompensationTask.reconstitute(
                7L, CompensationTask.TYPE_ORDER, "ORD-7", CompensationTask.OP_AUTO_CANCEL_ORDER,
                "{}", CompensationStatus.SUCCESS, 3, CompensationTask.MAX_RETRIES,
                "boom | MANUAL_COMPLETE", null, "trace-1", Instant.now(), Instant.now());
        when(compensationService.manualComplete(7L)).thenReturn(succeeded);

        var response = controller.complete(7L);

        assertThat(response.getData().status()).isEqualTo("SUCCESS");
        verify(compensationService).manualComplete(7L);
    }
}
