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
 * 后台发货应用服务（CHG-0019 REQ-M4-003）：PAID→SHIPPED CAS，记录物流公司/运单号。
 *
 * <p>重复发货幂等成功；非已支付状态 409；CAS 竞争落败重读仲裁。
 */
@Service
public class ShipmentService {

    private final OrderRepository orderRepository;

    public ShipmentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order ship(String orderNo, String deliveryCompany, String trackingNo, String operator) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        OrderStatus.TransitionOutcome outcome = order.outcome(OrderOperation.SHIP);
        if (outcome == OrderStatus.TransitionOutcome.ALREADY_TARGET) {
            return order;
        }
        if (outcome == OrderStatus.TransitionOutcome.ILLEGAL) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT, "当前订单状态不允许发货");
        }

        Instant now = Instant.now();
        OrderStatusHistory history = order.ship(operator, deliveryCompany, trackingNo, now);
        boolean won = orderRepository.transition(OrderRepository.StatusTransition.of(
                order, OrderOperation.SHIP, OrderStatus.SHIPPED, operator, null,
                order.deliveryCompany(), order.trackingNo(), history.occurredAt()));
        if (!won) {
            Order reloaded = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
            if (reloaded.status() != OrderStatus.SHIPPED) {
                throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                        "订单状态已变化，请刷新后重试");
            }
            return reloaded;
        }
        return order;
    }
}
