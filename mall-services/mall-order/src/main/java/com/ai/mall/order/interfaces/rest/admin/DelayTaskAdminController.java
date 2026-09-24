package com.ai.mall.order.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.order.admin.DelayTaskAdminService;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskRow;
import com.ai.mall.order.interfaces.rest.admin.dto.DelayTaskAdminDtos;
import com.ai.mall.order.interfaces.rest.admin.dto.DelayTaskAdminDtos.DelayTaskView;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端延迟取消任务台（STORY-009-04-01，/api/admin/order-delay/tasks，AC-031/032）。
 *
 * <p>三源派生视图（待取消 / 超时已取消 / 投递失败）支持分页与状态筛选；对待取消订单
 * 可人工介入取消。权限码 order-delay:list / order-delay:cancel；审计在 service 落日志。
 */
@RestController
@RequestMapping("/api/admin/order-delay/tasks")
public class DelayTaskAdminController {

    private final DelayTaskAdminService service;

    public DelayTaskAdminController(DelayTaskAdminService service) {
        this.service = service;
    }

    /** 人工取消请求体（可空；reason 可空）。 */
    public record CancelRequest(String reason) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order-delay:list')")
    public UnifyResult<OrderDtos.PageView<DelayTaskView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        DelayTaskAdminService.Page result = service.page(status, page, size);
        List<DelayTaskView> records = result.records().stream()
                .map(DelayTaskAdminController::toView).toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, result.total(), result.page(), result.size()));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('order-delay:cancel')")
    public UnifyResult<DelayTaskView> cancel(@PathVariable long orderId,
                                             @RequestBody(required = false) CancelRequest body) {
        String reason = body == null ? null : body.reason();
        DelayTaskRow row = service.cancel(orderId, reason, currentOperator());
        return UnifyResult.ok(toView(row));
    }

    private static String currentOperator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    private static DelayTaskView toView(DelayTaskRow row) {
        return new DelayTaskAdminDtos.DelayTaskView(
                row.getOrderId() == null ? 0L : row.getOrderId(),
                row.getOrderNo(), row.getDelayStatus(), row.getCreatedAt(),
                row.getCancelledAt(), row.getLastError());
    }
}
