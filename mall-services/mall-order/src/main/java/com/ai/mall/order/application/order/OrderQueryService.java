package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单查询应用服务（CHG-0019 REQ-M4-003）。
 *
 * <p>会员侧查询条件强制 memberId = 当前主体（归属收口），越权/不存在统一 404；
 * admin 侧不带归属条件。分页头不装配商品/历史，商品行由仓储批量装配避免 N+1。
 */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 会员订单分页。 */
    public OrderRepository.OrderPage memberPage(long memberId, OrderStatus status, Instant from, Instant to,
                                                int page, int size) {
        return orderRepository.page(OrderRepository.OrderPageQuery.member(memberId, status, from, to, page, size));
    }

    /** 会员订单详情（归属校验，越权 404）。 */
    public Order memberDetail(long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (order.memberId() != memberId) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return order;
    }

    /** 管理台订单分页。 */
    public OrderRepository.OrderPage adminPage(String orderNo, Long memberId, OrderStatus status, Instant from,
                                               Instant to, int page, int size) {
        return orderRepository.page(
                OrderRepository.OrderPageQuery.admin(orderNo, memberId, status, from, to, page, size));
    }

    /** 管理台订单详情。 */
    public Order adminDetail(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 批量取列表头商品行（Controller 装配摘要视图用）。 */
    public java.util.List<com.ai.mall.order.domain.order.OrderItem> itemsOf(java.util.List<Long> orderIds) {
        return orderRepository.findItemsByOrderIds(orderIds);
    }
}
