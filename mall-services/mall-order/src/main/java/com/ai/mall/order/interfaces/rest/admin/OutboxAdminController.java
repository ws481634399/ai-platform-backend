package com.ai.mall.order.interfaces.rest.admin;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.ai.mall.order.interfaces.rest.admin.dto.OutboxAdminDtos;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 Outbox 事件台（CHG-0025 M7 STORY-009-02-01，/api/admin/outbox/events）。
 *
 * <p>权限码 system:outbox:list / system:outbox:retry；可按状态/事件类型/聚合 ID 筛选，
 * 对 FAILED 记录手动重投（状态回 PENDING 并重置重试次数），重投操作写审计日志。</p>
 */
@RestController
@RequestMapping("/api/admin/outbox/events")
public class OutboxAdminController {

    private static final Logger auditLog = LoggerFactory.getLogger("outbox-audit");

    private final OutboxEventRepository repository;

    public OutboxAdminController(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:outbox:list')")
    public UnifyResult<OrderDtos.PageView<OutboxAdminDtos.OutboxView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = repository.page(status, eventType, aggregateId, Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<OutboxAdminDtos.OutboxView> records = result.records().stream()
                .map(OutboxAdminController::toView).toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:outbox:list')")
    public UnifyResult<OutboxAdminDtos.OutboxView> get(@PathVariable long id) {
        return repository.findById(id)
                .map(OutboxAdminController::toView)
                .map(UnifyResult::ok)
                .orElseGet(() -> UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "Outbox 记录不存在: " + id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('system:outbox:retry')")
    public UnifyResult<OutboxAdminDtos.OutboxView> retry(@PathVariable long id) {
        OutboxEvent before = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox 记录不存在: " + id));
        String operator = currentOperator();
        int updated = repository.resetForRetry(id);
        if (updated == 0) {
            throw new IllegalStateException("仅 FAILED 状态的记录可重投，当前状态: " + before.getStatus());
        }
        OutboxEvent after = repository.findById(id).orElse(before);
        auditLog.info("Outbox 手动重投: operator={}, id={}, beforeStatus={}, afterStatus={}, at={}",
                operator, id, before.getStatus(), after.getStatus(), java.time.Instant.now());
        return UnifyResult.ok(toView(after));
    }

    private static String currentOperator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    private static OutboxAdminDtos.OutboxView toView(OutboxEvent event) {
        return new OutboxAdminDtos.OutboxView(event.getId(), event.getAggregateId(), event.getEventType(),
                event.getPayload(), event.getStatus().name(), event.getRetryCount(), event.getNextRetryAt(),
                event.getTraceId(), event.getLastError(), event.getCreatedAt(), event.getSentAt());
    }
}
