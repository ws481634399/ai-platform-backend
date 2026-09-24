package com.ai.mall.order.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单状态内部端点（CHG-0025 M7 STORY-009-03-01）。
 *
 * <p>供 mall-inventory 消费乱序裁决回查真实订单状态；经安全链
 * {@code /api/internal/** → ROLE_SERVICE + X-Internal-Token} 保护，不暴露给外部会员。
 */
@RestController
@RequestMapping("/api/internal/orders")
public class OrderStatusInternalController {

    private final OrderRepository orderRepository;

    public OrderStatusInternalController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{orderId}/status")
    public UnifyResult<StatusView> status(@PathVariable long orderId) {
        OrderStatus status = orderRepository.findStatusById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        return UnifyResult.ok(new StatusView(orderId, status.name()));
    }

    /** 订单状态视图。 */
    public record StatusView(long orderId, String status) {
    }
}
