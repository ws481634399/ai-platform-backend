package com.ai.mall.order.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.compensation.CompensationService;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.ai.mall.order.interfaces.rest.admin.dto.AdminOrderDtos;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端交易补偿台（CHG-0019 REQ-M4-004，/api/admin/compensations）。
 *
 * <p>权限码 order:compensation；可分页查看任务并对 FAILED_DEAD/PENDING 任务手动重试。
 */
@RestController
@RequestMapping("/api/admin/compensations")
public class AdminCompensationController {

    private final CompensationService compensationService;

    public AdminCompensationController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:compensation')")
    public UnifyResult<OrderDtos.PageView<AdminOrderDtos.CompensationView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = compensationService.page(status, Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<AdminOrderDtos.CompensationView> records = result.records().stream()
                .map(AdminCompensationController::toView).toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, result.total(), result.page(), result.size()));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('order:compensation')")
    public UnifyResult<AdminOrderDtos.CompensationView> retry(@PathVariable long id) {
        CompensationTask task = compensationService.manualRetry(id);
        return UnifyResult.ok(toView(task));
    }

    private static AdminOrderDtos.CompensationView toView(CompensationTask task) {
        return new AdminOrderDtos.CompensationView(task.getId(), task.businessType(), task.businessId(),
                task.operation(), task.status().name(), task.retryCount(), task.maxRetries(), task.lastError(),
                task.nextRetryAt(), task.createdAt(), task.updatedAt());
    }
}
