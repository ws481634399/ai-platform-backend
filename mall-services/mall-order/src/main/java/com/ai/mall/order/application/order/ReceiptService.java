package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderOperation;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.domain.order.OrderStatusHistory;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 会员确认收货应用服务（CHG-0019 REQ-M4-003）：SHIPPED→COMPLETED CAS。
 *
 * <p>重复确认幂等成功；非待收货状态 409；CAS 竞争落败重读仲裁。
 */
@Service
public class ReceiptService {

    private final OrderRepository orderRepository;

    public ReceiptService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order confirmReceipt(long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (order.memberId() != memberId) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        OrderStatus.TransitionOutcome outcome = order.outcome(OrderOperation.CONFIRM_RECEIPT);
        if (outcome == OrderStatus.TransitionOutcome.ALREADY_TARGET) {
            return order;
        }
        if (outcome == OrderStatus.TransitionOutcome.ILLEGAL) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT, "当前订单状态不允许确认收货");
        }

        Instant now = Instant.now();
        String operator = Long.toString(memberId);
        OrderStatusHistory history = order.confirmReceipt(operator, now);
        boolean won = orderRepository.transition(OrderRepository.StatusTransition.of(
                order, OrderOperation.CONFIRM_RECEIPT, OrderStatus.COMPLETED, operator, null, null, null,
                history.occurredAt()));
        if (!won) {
            Order reloaded = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
            if (reloaded.status() != OrderStatus.COMPLETED) {
                throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                        "订单状态已变化，请刷新后重试");
            }
            return reloaded;
        }
        return order;
    }
}
