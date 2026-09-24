package com.ai.mall.order.application.order.timeout;

import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderRepository.ExpiredOrder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时兜底扫描器（STORY-009-04-01，AC-030）。
 *
 * <p>延迟消息为主路径（精度高、无空轮），本扫描为双保险：延迟消息因 broker 故障/积压/
 * 投递失败而未到期触发时，按 createdAt + 超时分钟数捞出仍 PENDING_PAYMENT 的订单逐单
 * 系统取消。与延迟路径共用 {@link OrderCancelService#systemCancel}：CAS 保证两条路径
 * 同时命中一单时只产生一次真实取消。
 *
 * <p>单条异常 catch 隔离，不影响其他订单；默认开启，可用
 * mall.order.timeout-fallback.enabled=false 关闭。
 */
@Component
@ConditionalOnProperty(name = "mall.order.timeout-fallback.enabled", matchIfMissing = true)
public class OrderTimeoutFallbackScanner {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutFallbackScanner.class);

    private static final String CANCEL_REASON = "PAYMENT_TIMEOUT";
    private static final String CANCEL_SOURCE = "TIMEOUT_FALLBACK";

    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;
    private final PaymentTimeoutPolicy timeoutPolicy;
    private final int batchLimit;

    public OrderTimeoutFallbackScanner(OrderRepository orderRepository, OrderCancelService orderCancelService,
                                       PaymentTimeoutPolicy timeoutPolicy,
                                       @Value("${mall.order.timeout-fallback.batch-limit:100}") int batchLimit) {
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
        this.timeoutPolicy = timeoutPolicy;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${mall.order.timeout-fallback.fixed-delay-ms:60000}")
    public void scan() {
        Instant cutoff = Instant.now().minus(timeoutPolicy.timeoutMinutes(), ChronoUnit.MINUTES);
        List<ExpiredOrder> expired;
        try {
            expired = orderRepository.findExpiredPending(cutoff, batchLimit);
        } catch (RuntimeException ex) {
            log.warn("超时兜底扫描查询失败，等待下一轮 cutoff={}", cutoff, ex);
            return;
        }
        if (expired.isEmpty()) {
            return;
        }
        log.info("超时兜底扫描发现 {} 笔待取消订单 cutoff={}", expired.size(), cutoff);
        for (ExpiredOrder order : expired) {
            try {
                orderCancelService.systemCancel(order.id(), CANCEL_REASON, CANCEL_SOURCE);
            } catch (RuntimeException ex) {
                // 单条隔离：可能与延迟消息/支付竞争，下一轮或人工介入再裁决
                log.warn("超时兜底取消失败 orderId={}, orderNo={}", order.id(), order.orderNo(), ex);
            }
        }
    }
}
