package com.ai.mall.order.application.order.event;

import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventVersions;
import com.ai.mall.event.payload.OrderCancelledEventPayload;
import com.ai.mall.event.payload.OrderCompletedEventPayload;
import com.ai.mall.event.payload.OrderCreatedEventPayload;
import com.ai.mall.event.payload.OrderDelayPayload;
import com.ai.mall.event.payload.PaymentSucceededEventPayload;
import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 订单事件信封组装器：从订单聚合当前状态构造 §41 Payload 与 Envelope。
 *
 * <p>裁决字段（聚合上不存在的 §41 字段）：
 * <ul>
 *   <li>reservationNo 取 orderNo——订单级预留组标识；per-line reservationId = orderNo:skuId；</li>
 *   <li>M4 为模拟支付无支付单号 → paymentNo 用确定性编号 "PAY"+orderNo；</li>
 *   <li>paymentDeadline / expireAt = createdAt + 本次 flush 读取到的超时分钟数。</li>
 * </ul>
 */
@Component
public class OrderEnvelopeAssembler {

    private static final String PRODUCER = "mall-order";
    private static final String CURRENCY_CNY = "CNY";

    private final ObjectMapper objectMapper;

    public OrderEnvelopeAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按事件类型组装完整 Envelope。
     *
     * @param timeoutMinutes 本次 flush 统一使用的支付超时分钟数（调用方保证一次 flush 只读一次）
     */
    public Envelope assemble(Order order, OrderIntegrationEvent event, long timeoutMinutes) {
        Object payloadDto = buildPayload(order, event.type(), timeoutMinutes);
        JsonNode payloadNode = objectMapper.valueToTree(payloadDto);
        return Envelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(toTag(event.type()))
                .eventVersion(EventVersions.CURRENT)
                .occurredAt(Instant.now())
                .producer(PRODUCER)
                .traceId(TraceContext.get())
                .payload(payloadNode)
                .build();
    }

    private Object buildPayload(Order order, OrderIntegrationEventType type, long timeoutMinutes) {
        String orderId = String.valueOf(order.getId());
        String memberId = String.valueOf(order.memberId());
        return switch (type) {
            case ORDER_CREATED -> new OrderCreatedEventPayload(
                    orderId, order.orderNo(), memberId, order.orderNo(),
                    order.money().payFen(), CURRENCY_CNY,
                    order.createdAt().plus(timeoutMinutes, ChronoUnit.MINUTES));
            case PAYMENT_SUCCEEDED -> new PaymentSucceededEventPayload(
                    orderId, order.orderNo(), "PAY" + order.orderNo(), order.orderNo(),
                    order.money().payFen(), CURRENCY_CNY, order.paidAt());
            case ORDER_CANCELLED -> new OrderCancelledEventPayload(
                    orderId, order.orderNo(), order.orderNo(),
                    order.cancelReason(), order.cancelledAt());
            case ORDER_COMPLETED -> new OrderCompletedEventPayload(
                    orderId, order.orderNo(), memberId, order.completedAt());
            case PAYMENT_TIMEOUT_CHECK -> new OrderDelayPayload(
                    orderId, order.orderNo(),
                    order.createdAt().plus(timeoutMinutes, ChronoUnit.MINUTES));
        };
    }

    /** 领域事件类型 → 契约 Tag 字符串。 */
    public static String toTag(OrderIntegrationEventType type) {
        return switch (type) {
            case ORDER_CREATED -> EventTags.ORDER_CREATED;
            case PAYMENT_SUCCEEDED -> EventTags.PAYMENT_SUCCEEDED;
            case ORDER_CANCELLED -> EventTags.ORDER_CANCELLED;
            case ORDER_COMPLETED -> EventTags.ORDER_COMPLETED;
            case PAYMENT_TIMEOUT_CHECK -> EventTags.PAYMENT_TIMEOUT_CHECK;
        };
    }
}
