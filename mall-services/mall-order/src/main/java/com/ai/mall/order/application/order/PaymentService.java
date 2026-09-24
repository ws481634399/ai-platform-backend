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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单支付应用服务（CHG-0019 REQ-M4-002）。
 *
 * <p>M4 支付为模拟支付：状态 CAS（PENDING_PAYMENT→PAID）成功后，事务外逐行确认扣减库存预留。
 * 重复支付幂等返回成功；与取消竞争（CAS 落败）重读订单：已支付幂等返回，其余 409。
 * confirm 失败不回滚支付结果：订单保持 PAID，登记 CONFIRM 补偿并 WARN，接口仍按成功返回。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final OrderCompensationPort compensationPort;
    private final IntegrationMode integrationMode;

    public PaymentService(OrderRepository orderRepository, InventoryPort inventoryPort,
                          OrderCompensationPort compensationPort, IntegrationMode integrationMode) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.compensationPort = compensationPort;
        this.integrationMode = integrationMode;
    }

    public Order pay(long memberId, String orderNo) {
        Order order = loadOwned(orderNo, memberId);
        OrderStatus.TransitionOutcome outcome = order.outcome(OrderOperation.PAY);
        if (outcome == OrderStatus.TransitionOutcome.ALREADY_TARGET) {
            // 重复支付：幂等成功返回当前订单
            return order;
        }
        if (outcome == OrderStatus.TransitionOutcome.ILLEGAL) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "当前订单状态不允许支付");
        }

        Instant now = Instant.now();
        String operator = Long.toString(memberId);
        OrderStatusHistory history = order.pay(operator, now);
        boolean won = orderRepository.transition(OrderRepository.StatusTransition.of(
                order, OrderOperation.PAY, OrderStatus.PAID, operator, null, null, null, history.occurredAt()));
        if (!won) {
            // 与取消/并发支付竞争：重读仲裁
            Order reloaded = loadOwned(orderNo, memberId);
            if (reloaded.status() == OrderStatus.PAID) {
                return reloaded;
            }
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
        }

        if (integrationMode.async()) {
            // 异步事件驱动：PAYMENT_SUCCEEDED 已随事务入 Outbox，库存确认扣减由 mall-inventory 消费者完成
            return order;
        }
        // MQ 关闭降级：同步确认扣减库存（幂等）；失败登记补偿，支付仍算成功
        log.warn("rocketmq.enabled=false，支付后库存确认扣减走同步降级路径 orderNo={}", order.orderNo());
        confirmAfterPaid(order);
        return order;
    }

    private void confirmAfterPaid(Order order) {
        List<OrderCompensationPort.InventoryLine> failed = new ArrayList<>();
        for (OrderItem item : order.items()) {
            String reservationId = order.orderNo() + ":" + item.skuId();
            try {
                inventoryPort.confirm(reservationId);
            } catch (RuntimeException ex) {
                log.warn("支付后确认扣减失败 orderNo={}, reservationId={}", order.orderNo(), reservationId, ex);
                failed.add(new OrderCompensationPort.InventoryLine(item.skuId(), item.quantity(), reservationId));
            }
        }
        if (!failed.isEmpty()) {
            compensationPort.enqueueInventoryConfirm(order.orderNo(), failed, "支付后确认扣减失败");
        }
    }

    private Order loadOwned(String orderNo, long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (order.memberId() != memberId) {
            // 越权访问与不存在统一 404，不泄露单号是否存在
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return order;
    }
}
