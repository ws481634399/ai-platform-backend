package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 会员取消订单应用服务（CHG-0019 REQ-M4-002）。
 *
 * <p>仅待支付可取消；CAS（PENDING_PAYMENT→CANCELLED）成功后事务外逐行释放库存预留。
 * 重复取消幂等返回成功；与支付竞争落败时重读：已取消幂等返回，已支付 409。
 * release 失败不影响取消结果：登记 RELEASE 补偿，由调度器有界重试。
 */
@Service
public class OrderCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelService.class);

    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final OrderCompensationPort compensationPort;

    public OrderCancelService(OrderRepository orderRepository, InventoryPort inventoryPort,
                              OrderCompensationPort compensationPort) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.compensationPort = compensationPort;
    }

    public Order cancel(long memberId, String orderNo, String reason) {
        Order order = loadOwned(orderNo, memberId);
        OrderStatus.TransitionOutcome outcome = order.outcome(OrderOperation.CANCEL);
        if (outcome == OrderStatus.TransitionOutcome.ALREADY_TARGET) {
            return order;
        }
        if (outcome == OrderStatus.TransitionOutcome.ILLEGAL) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "当前订单状态不允许取消");
        }

        Instant now = Instant.now();
        String operator = Long.toString(memberId);
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        OrderStatusHistory history = order.cancel(operator, normalizedReason, now);
        boolean won = orderRepository.transition(OrderRepository.StatusTransition.of(
                order, OrderOperation.CANCEL, OrderStatus.CANCELLED, operator, normalizedReason,
                null, null, history.occurredAt()));
        if (!won) {
            Order reloaded = loadOwned(orderNo, memberId);
            if (reloaded.status() == OrderStatus.CANCELLED) {
                return reloaded;
            }
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "订单状态已变化，请刷新后重试");
        }

        // 事务提交后释放全部库存预留（幂等）；失败行登记补偿
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
