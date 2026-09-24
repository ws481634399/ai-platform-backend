package com.ai.mall.order.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.compensation.CompensationService;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.ai.mall.order.interfaces.rest.admin.dto.AdminOrderDtos;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端交易补偿台（CHG-0019 REQ-M4-004；CHG-0025 STORY-009-05-01 增强）。
 *
 * <p>支持按操作类型/聚合 ID/状态筛选并查看 payload；可手动重试、手动标记完成（均审计，AC-038）。
 */
@RestController
@RequestMapping("/api/admin/compensations")
public class AdminCompensationController {

    /** 操作类型白名单（非法值忽略，避免任意列值透传）。 */
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            CompensationTask.OP_CONFIRM_INVENTORY,
            CompensationTask.OP_RELEASE_INVENTORY,
            CompensationTask.OP_AUTO_CANCEL_ORDER);

    /** 补偿人工操作审计日志（操作人/任务/前后状态/时间，AC-038）。 */
    private static final Logger audit = LoggerFactory.getLogger("compensation-audit");

    private final CompensationService compensationService;

    public AdminCompensationController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:compensation:list')")
    public UnifyResult<OrderDtos.PageView<AdminOrderDtos.CompensationView>> page(
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String safeOperation = normalizeOperation(operation);
        String safeAggregateId = escapeLike(aggregateId);
        var result = compensationService.page(
                safeOperation, safeAggregateId, normalizeStatus(status),
                Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<AdminOrderDtos.CompensationView> records = result.records().stream()
                .map(AdminCompensationController::toView).toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, result.total(), result.page(), result.size()));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('system:compensation:retry')")
    public UnifyResult<AdminOrderDtos.CompensationView> retry(@PathVariable long id) {
        CompensationTask task = compensationService.manualRetry(id);
        audit.info("compensation manual retry operator={}, taskId={}, after={}, at={}",
                currentOperator(), id, task.status(), Instant.now());
        return UnifyResult.ok(toView(task));
    }

    /** 手动标记完成（AC-038）：人工确认业务闭环，置 SUCCESS 并审计。 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('system:compensation:complete')")
    public UnifyResult<AdminOrderDtos.CompensationView> complete(@PathVariable long id) {
        CompensationTask task = compensationService.manualComplete(id);
        audit.info("compensation manual complete operator={}, taskId={}, after={}, at={}",
                currentOperator(), id, task.status(), Instant.now());
        return UnifyResult.ok(toView(task));
    }

    private static AdminOrderDtos.CompensationView toView(CompensationTask task) {
        return new AdminOrderDtos.CompensationView(task.getId(), task.businessType(), task.businessId(),
                task.operation(), task.payload(), task.status().name(), task.retryCount(), task.maxRetries(),
                task.lastError(), task.nextRetryAt(), task.createdAt(), task.updatedAt(), task.traceId());
    }

    /** operation 白名单校验：空白/非法返回 null（不按此过滤）。 */
    private static String normalizeOperation(String operation) {
        if (operation == null) {
            return null;
        }
        String trimmed = operation.trim();
        return ALLOWED_OPERATIONS.contains(trimmed) ? trimmed : null;
    }

    /** status 白名单校验：非法值忽略。 */
    private static String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        for (CompensationStatus candidate : CompensationStatus.values()) {
            if (candidate.name().equals(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * LIKE 通配符转义：用户输入中的 %/_ 作普通字符处理，防越权扩大匹配；
     * 空白输入返回 null（不过滤）。
     */
    private static String escapeLike(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 当前操作人（审计用）；未认证上下文归 system。 */
    private static String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
