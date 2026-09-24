package com.ai.mall.order.application.order.consumer;

import com.ai.mall.common.mq.consumer.AbstractIntegrationHandler;
import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.mq.consumer.IntegrationEventListener;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.event.ConsumerGroups;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import com.ai.mall.event.payload.OrderDelayPayload;
import com.ai.mall.order.application.compensation.CompensationService;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PAYMENT_TIMEOUT_CHECK 延迟消息到期回查消费者（STORY-009-04-01，AC-028/029）。
 *
 * <p>延迟消息只负责"到期唤醒"，真正裁决以订单聚合当前状态为准：
 * <ul>
 *   <li>订单不存在 / 非 PENDING_PAYMENT（用户已支付或已取消）→ markSkipped 幂等收口，不取消；</li>
 *   <li>仍 PENDING_PAYMENT → 调 {@link OrderCancelService#systemCancel}，复用 M4 全流程
 *       （CAS + ORDER_CANCELLED Outbox + 库存释放链路）；</li>
 *   <li>CAS 竞争落败（支付与取消同时发生）→ BusinessException STATUS_CONFLICT 归并为 SKIPPED；</li>
 *   <li>其他异常向上传播 → RocketMQ 重试，超限进 DLQ 由人工/补偿介入。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@IntegrationEventListener(
        topic = EventTopics.AIMALL_ORDER_DELAY,
        eventType = EventTags.PAYMENT_TIMEOUT_CHECK,
        consumerGroup = ConsumerGroups.ORDER_DELAY_CONSUMER_GROUP,
        maxSupportedVersion = 1)
public class PaymentTimeoutCheckHandler extends AbstractIntegrationHandler<OrderDelayPayload> {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutCheckHandler.class);

    private static final String CANCEL_REASON = "PAYMENT_TIMEOUT";
    private static final String CANCEL_SOURCE = "DELAY_MESSAGE";

    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;
    private final CompensationService compensationService;

    public PaymentTimeoutCheckHandler(ObjectMapper objectMapper, IdempotentConsumer idempotentConsumer,
                                      OrderRepository orderRepository, OrderCancelService orderCancelService,
                                      CompensationService compensationService) {
        super(objectMapper, idempotentConsumer);
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
        this.compensationService = compensationService;
    }

    @Override
    protected void handle(Envelope envelope, OrderDelayPayload payload) {
        long orderId;
        try {
            orderId = Long.parseLong(payload.orderId());
        } catch (NumberFormatException ex) {
            // payload 非法：保守不丢消息，交重试最终 DLQ（不 markSkipped 掩盖问题）
            throw new IllegalArgumentException("延迟事件 orderId 非法: " + payload.orderId(), ex);
        }

        Optional<OrderStatus> current = orderRepository.findStatusById(orderId);
        if (current.isEmpty()) {
            log.warn("延迟到期回查：订单已不存在，跳过 eventId={}, orderId={}", envelope.getEventId(), orderId);
            markSkipped(envelope.getEventId(), ConsumerGroups.ORDER_DELAY_CONSUMER_GROUP);
            return;
        }
        if (current.get() != OrderStatus.PENDING_PAYMENT) {
            log.warn("延迟到期回查：订单状态={} 非待支付，跳过 eventId={}, orderId={}",
                    current.get(), envelope.getEventId(), orderId);
            markSkipped(envelope.getEventId(), ConsumerGroups.ORDER_DELAY_CONSUMER_GROUP);
            return;
        }

        try {
            orderCancelService.systemCancel(orderId, CANCEL_REASON, CANCEL_SOURCE);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == OrderErrorCode.STATUS_CONFLICT) {
                // 与支付竞争落败（CAS 期间状态翻转）→ 与"非 PENDING"同语义，归并 SKIPPED
                log.warn("延迟取消与支付竞争落败，归并跳过 eventId={}, orderId={}",
                        envelope.getEventId(), orderId);
                markSkipped(envelope.getEventId(), ConsumerGroups.ORDER_DELAY_CONSUMER_GROUP);
                return;
            }
            // 其他业务异常（下游失败等）：登记 ORDER_AUTO_CANCEL 补偿后重抛（AC-035）
            registerAutoCancelCompensation(envelope, payload, orderId);
            throw ex;
        } catch (RuntimeException ex) {
            // 系统/DB 异常：同样登记补偿兜底，再交 MQ 重试
            registerAutoCancelCompensation(envelope, payload, orderId);
            throw ex;
        }
    }

    /** 登记订单自动取消补偿；登记本身失败不掩盖原始异常（内部已 catch 记 ERROR）。 */
    private void registerAutoCancelCompensation(Envelope envelope, OrderDelayPayload payload, long orderId) {
        compensationService.enqueueOrderAutoCancel(
                orderId, payload.orderNo(), envelope.getEventId(), envelope.getTraceId());
    }
}
