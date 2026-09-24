package com.ai.mall.order.application.order.admin;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskAdminMapper;
import com.ai.mall.order.infrastructure.persistence.order.DelayTaskRow;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 延迟取消任务管理编排服务（STORY-009-04-01）：分页查询三源派生视图、人工介入取消。
 *
 * <p>人工取消复用 {@link OrderCancelService#systemCancel}，CAS 保证与延迟消息/兜底扫描/
 * 用户支付并发安全；独立审计日志（order-delay-audit）记录操作人/前后状态/traceId。
 */
@Service
public class DelayTaskAdminService {

    private static final Logger auditLog = LoggerFactory.getLogger("order-delay-audit");

    private static final String DEFAULT_REASON = "ADMIN_MANUAL";

    private final DelayTaskAdminMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;

    public DelayTaskAdminService(DelayTaskAdminMapper mapper, OrderRepository orderRepository,
                                 OrderCancelService orderCancelService) {
        this.mapper = mapper;
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
    }

    /** 分页结果（与仓储 page 同构）。 */
    public record Page(List<DelayTaskRow> records, long total, int page, int size) {
    }

    /** 分页查询：非法/空白 status 忽略（返回全部），size 上限 100。 */
    public Page page(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalized = normalizeStatus(status);
        int offset = (safePage - 1) * safeSize;
        List<DelayTaskRow> records = mapper.selectTasks(normalized, offset, safeSize);
        return new Page(records, mapper.countTasks(normalized), safePage, safeSize);
    }

    /**
     * 人工取消。
     *
     * @param reason   取消原因；空白用 ADMIN_MANUAL
     * @param operator 操作人（控制器从安全上下文取）
     */
    public DelayTaskRow cancel(long orderId, String reason, String operator) {
        OrderStatus before = orderRepository.findStatusById(orderId).orElse(null);
        String normalizedReason = reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim();
        Order order = orderCancelService.systemCancel(orderId, normalizedReason, DEFAULT_REASON);
        auditLog.info("延迟任务人工取消: operator={}, orderId={}, beforeStatus={}, afterStatus={}, "
                        + "traceId={}, at={}",
                operator, orderId, before, order.status(), TraceContext.get(), Instant.now());
        return toRow(order);
    }

    private DelayTaskRow toRow(Order order) {
        DelayTaskRow row = new DelayTaskRow();
        row.setOrderId(order.getId());
        row.setOrderNo(order.orderNo());
        row.setDelayStatus(order.status().name());
        row.setCreatedAt(order.createdAt());
        row.setCancelledAt(order.cancelledAt());
        return row;
    }

    /** 状态白名单裁决：null/空白/非法值 → null（不筛选）。 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PENDING", "CANCELLED", "FAILED" -> upper;
            default -> null;
        };
    }
}
