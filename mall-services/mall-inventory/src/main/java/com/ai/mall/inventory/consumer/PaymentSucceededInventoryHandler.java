package com.ai.mall.inventory.consumer;

import com.ai.mall.common.mq.consumer.AbstractIntegrationHandler;
import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.mq.consumer.IntegrationEventListener;
import com.ai.mall.event.ConsumerGroups;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import com.ai.mall.event.payload.PaymentSucceededEventPayload;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ConfirmCommand;
import com.ai.mall.inventory.domain.inventory.InventoryRepository;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient.CompensationRequest;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient.Line;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PAYMENT_SUCCEEDED 库存消费者（CHG-0025 M7 STORY-009-03-01，AC-020/023）。
 *
 * <p>消费流程：回查订单真实状态裁决乱序 → 订单已 CANCELLED 则跳过（防对已取消单确认扣减），
 * 否则枚举 orderNo:% 全部预留逐行确认扣减（DEDUCTED 终态幂等返回）；
 * confirm 异常时先向订单侧登记 INVENTORY_CONFIRM_DEDUCT 补偿，再抛出交 MQ 重试/DLQ。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@IntegrationEventListener(
        topic = EventTopics.AIMALL_ORDER_EVENTS,
        eventType = EventTags.PAYMENT_SUCCEEDED,
        consumerGroup = ConsumerGroups.INVENTORY_CONSUMER_GROUP,
        maxSupportedVersion = 1)
public class PaymentSucceededInventoryHandler extends AbstractIntegrationHandler<PaymentSucceededEventPayload> {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededInventoryHandler.class);

    private static final String ORDER_CANCELLED = "CANCELLED";
    private static final String COMPENSATION_CONFIRM = "INVENTORY_CONFIRM_DEDUCT";

    private final InventoryApplicationService inventoryApplicationService;
    private final InventoryRepository inventoryRepository;
    private final OrderServiceClient orderServiceClient;

    public PaymentSucceededInventoryHandler(ObjectMapper objectMapper, IdempotentConsumer idempotentConsumer,
                                            InventoryApplicationService inventoryApplicationService,
                                            InventoryRepository inventoryRepository,
                                            OrderServiceClient orderServiceClient) {
        super(objectMapper, idempotentConsumer);
        this.inventoryApplicationService = inventoryApplicationService;
        this.inventoryRepository = inventoryRepository;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    protected void handle(Envelope envelope, PaymentSucceededEventPayload payload) {
        // 乱序裁决：以订单服务的真实状态为准（传输异常向上传播 → MQ 重试）
        String status = orderServiceClient.getStatus(payload.orderId());
        if (ORDER_CANCELLED.equals(status)) {
            log.warn("PAYMENT_SUCCEEDED 乱序到达：订单已取消，跳过确认扣减 eventId={}, orderNo={}",
                    envelope.getEventId(), payload.orderNo());
            markSkipped(envelope.getEventId(), consumerGroup());
            return;
        }

        List<String> reservationIds = inventoryRepository.findReservationIdsByOrderNo(payload.orderNo());
        for (String reservationId : reservationIds) {
            try {
                // 应用服务对 DEDUCTED 终态幂等返回，重复投递安全
                inventoryApplicationService.confirmDeduction(new ConfirmCommand(reservationId));
            } catch (RuntimeException ex) {
                log.warn("消费确认扣减失败，先登记补偿再交重试 orderNo={}, reservationId={}",
                        payload.orderNo(), reservationId, ex);
                registerConfirmCompensation(payload, reservationId, ex);
                throw ex;
            }
        }
    }

    /** 失败行登记 INVENTORY_CONFIRM_DEDUCT 补偿；补偿登记本身失败不掩盖原始异常。 */
    private void registerConfirmCompensation(PaymentSucceededEventPayload payload, String reservationId,
                                             RuntimeException cause) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(reservationId).orElse(null);
        List<Line> lines;
        if (reservation != null) {
            lines = List.of(new Line(reservation.getSkuId(), (int) reservation.getQuantity(), reservationId));
        } else {
            // 预留详情查不到时仍登记，skuId 从 reservationId 后缀解析兜底
            long skuId = parseSkuId(reservationId);
            lines = List.of(new Line(skuId, 0, reservationId));
        }
        try {
            orderServiceClient.registerCompensation(new CompensationRequest(
                    payload.orderId(), payload.orderNo(), COMPENSATION_CONFIRM,
                    "消费 PAYMENT_SUCCEEDED 确认扣减失败: " + cause.getMessage(), lines));
        } catch (RuntimeException compensationError) {
            log.error("登记确认扣减补偿失败 orderNo={}, reservationId={}",
                    payload.orderNo(), reservationId, compensationError);
        }
    }

    /** orderNo:skuId → skuId；解析失败返回 0。 */
    private static long parseSkuId(String reservationId) {
        int idx = reservationId.lastIndexOf(':');
        if (idx < 0 || idx == reservationId.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(reservationId.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String consumerGroup() {
        return getClass().getAnnotation(IntegrationEventListener.class).consumerGroup();
    }
}
