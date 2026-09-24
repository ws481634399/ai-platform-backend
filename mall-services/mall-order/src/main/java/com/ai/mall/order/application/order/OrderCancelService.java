package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.event.IntegrationMode;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderOperation;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.domain.order.OrderStatusHistory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单取消应用服务（CHG-0019 REQ-M4-002；STORY-009-04-01 增加系统取消入口）。
 *
 * <p>仅待支付可取消；CAS（PENDING_PAYMENT→CANCELLED）成功后，同步降级模式于事务外逐行
 * 释放库存预留。重复取消幂等返回成功；与支付竞争落败时重读：已取消幂等返回，其他 409。
 * release 失败不影响取消结果：登记 RELEASE 补偿，由调度器有界重试。
 *
 * <p>{@link #systemCancel} 供延迟消息到期回查 / 定时补偿兜底调用：按主键加载不做会员
 * 归属，操作人固定 "SYS:"+source 以区分来源（DELAY_MESSAGE / TIMEOUT_FALLBACK）。
 */
@Service
public class OrderCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelService.class);

    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final OrderCompensationPort compensationPort;
    private final IntegrationMode integrationMode;

    public OrderCancelService(OrderRepository orderRepository, InventoryPort inventoryPort,
                              OrderCompensationPort compensationPort, IntegrationMode integrationMode) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.compensationPort = compensationPort;
        this.integrationMode = integrationMode;
    }

    /** 会员侧取消：加载时做归属校验（查不到或非本人均按 NOT_FOUND 处理）。 */
    public Order cancel(long memberId, String orderNo, String reason) {
        Order order = loadOwned(orderNo, memberId);
        return doCancel(order, Long.toString(memberId), reason, () -> loadOwned(orderNo, memberId));
    }

    /**
     * 系统自动取消（延迟消息到期 / 定时补偿兜底）。
     *
     * @param reason 取消原因（如 PAYMENT_TIMEOUT）
     * @param source 触发来源标识（DELAY_MESSAGE / TIMEOUT_FALLBACK），记入操作人前缀
     */
    public Order systemCancel(long orderId, String reason, String source) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        return doCancel(order, "SYS:" + source, reason,
                () -> orderRepository.findById(orderId)
                        .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND)));
    }

    /**
     * 取消主流程（会员 / 系统共用）。
     *
     * @param operator 操作人：会员 id 或 SYS:source
     * @param reloader CAS 落败后的重读器（各自保持归属语义）
     */
    private Order doCancel(Order order, String operator, String reason, Supplier<Order> reloader) {
        OrderStatus.TransitionOutcome outcome = order.outcome(OrderOperation.CANCEL);
        if (outcome == OrderStatus.TransitionOutcome.ALREADY_TARGET) {
            return order;
        }
        if (outcome == OrderStatus.TransitionOutcome.ILLEGAL) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "当前订单状态不允许取消");
        }

        Instant now = Instant.now();
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        OrderStatusHistory history = order.cancel(operator, normalizedReason, now);
        boolean won = orderRepository.transition(OrderRepository.StatusTransition.of(
                order, OrderOperation.CANCEL, OrderStatus.CANCELLED, operator, normalizedReason,
                null, null, history.occurredAt()));
        if (!won) {
            Order reloaded = reloader.get();
            if (reloaded.status() == OrderStatus.CANCELLED) {
                return reloaded;
            }
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "订单状态已变化，请刷新后重试");
        }

        if (integrationMode.async()) {
            // 异步事件驱动：ORDER_CANCELLED 已随事务入 Outbox，库存释放由 mall-inventory 消费者完成
            return order;
        }
        // MQ 关闭降级：同步释放全部库存预留（幂等）；失败行登记补偿
        log.warn("rocketmq.enabled=false，取消后库存释放走同步降级路径 orderNo={}", order.orderNo());
        releaseAfterCancel(order);
        return order;
    }

    private void releaseAfterCancel(Order order) {
        List<OrderCompensationPort.InventoryLine> failed = new ArrayList<>();
        for (OrderItem item : order.items()) {
            String reservationId = order.orderNo() + ":" + item.skuId();
            try {
                inventoryPort.release(reservationId);
            } catch (RuntimeException ex) {
                log.warn("取消后释放预留失败 orderNo={}, reservationId={}", order.orderNo(), reservationId, ex);
                failed.add(new OrderCompensationPort.InventoryLine(item.skuId(), item.quantity(), reservationId));
            }
        }
        if (!failed.isEmpty()) {
            compensationPort.enqueueInventoryRelease(order.orderNo(), failed, "取消后释放库存失败");
        }
    }

    private Order loadOwned(String orderNo, long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (order.memberId() != memberId) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return order;
    }
}
