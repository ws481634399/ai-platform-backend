package com.ai.mall.order.interfaces.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.order.application.order.admin.DelayTaskAdminService;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 延迟任务管理端控制器直调测试（DTO/筛选透传）。
 */
@ExtendWith(MockitoExtension.class)
class DelayTaskAdminControllerTest {

    @Mock
    private DelayTaskAdminService service;

    private DelayTaskAdminController controller() {
        return new DelayTaskAdminController(service);
    }

    private DelayTaskRow row() {
        DelayTaskRow row = new DelayTaskRow();
        row.setOrderId(1L);
        row.setOrderNo("ON1");
        row.setDelayStatus("PENDING");
        row.setCreatedAt(Instant.now());
        return row;
    }

    @Test
    void page_returnsPageViewWithMappedFields() {
        when(service.page("PENDING", 1, 10))
                .thenReturn(new DelayTaskAdminService.Page(List.of(row()), 1L, 1, 10));

        var result = controller().page("PENDING", 1, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().records()).hasSize(1);
        assertThat(result.getData().total()).isEqualTo(1L);
        assertThat(result.getData().records().get(0).orderNo()).isEqualTo("ON1");
    }

    @Test
    void cancel_passesReasonFromBody() {
        DelayTaskRow row = row();
        row.setDelayStatus("CANCELLED");
        when(service.cancel(eq(1L), eq("运营介入"), eq("unknown"))).thenReturn(row);

        var result = controller().cancel(1L, new DelayTaskAdminController.CancelRequest("运营介入"));

        assertThat(result.isSuccess()).isTrue();
        verify(service).cancel(1L, "运营介入", "unknown");
    }

    @Test
    void cancel_emptyBody_usesDefaultReason() {
        DelayTaskRow row = row();
        row.setDelayStatus("CANCELLED");
        when(service.cancel(eq(1L), eq(null), eq("unknown"))).thenReturn(row);

        controller().cancel(1L, null);

        verify(service).cancel(1L, null, "unknown");
    }
}
