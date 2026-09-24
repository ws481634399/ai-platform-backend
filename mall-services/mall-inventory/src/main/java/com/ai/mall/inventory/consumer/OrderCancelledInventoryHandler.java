package com.ai.mall.inventory.consumer;

import com.ai.mall.common.mq.consumer.AbstractIntegrationHandler;
import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.mq.consumer.IntegrationEventListener;
import com.ai.mall.event.ConsumerGroups;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import com.ai.mall.event.payload.OrderCancelledEventPayload;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ReleaseCommand;
import com.ai.mall.inventory.domain.inventory.InventoryRepository;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient.CompensationRequest;
import com.ai.mall.inventory.infrastructure.client.OrderServiceClient.Line;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ORDER_CANCELLED 库存消费者（CHG-0025 M7 STORY-009-03-01，AC-021/023）。
 *
 * <p>消费流程：回查订单真实状态裁决乱序 → 确为 CANCELLED 才枚举 orderNo:% 预留逐行释放
 * （RELEASED 终态幂等返回）；真实状态为 PAID/SHIPPED/COMPLETED 时跳过，防误释放已成交库存；
 * release 异常先登记 INVENTORY_RELEASE 补偿，再抛出交 MQ 重试/DLQ。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@IntegrationEventListener(
        topic = EventTopics.AIMALL_ORDER_EVENTS,
        eventType = EventTags.ORDER_CANCELLED,
        consumerGroup = ConsumerGroups.INVENTORY_CONSUMER_GROUP,
        maxSupportedVersion = 1)
public class OrderCancelledInventoryHandler extends AbstractIntegrationHandler<OrderCancelledEventPayload> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledInventoryHandler.class);

    /** 这些状态下禁止释放（取消事件乱序/伪造，库存已进入或走完履约）。 */
    private static final Set<String> RELEASE_FORBIDDEN_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");

    private static final String ORDER_CANCELLED = "CANCELLED";
    private static final String COMPENSATION_RELEASE = "INVENTORY_RELEASE";

    private final InventoryApplicationService inventoryApplicationService;
    private final InventoryRepository inventoryRepository;
    private final OrderServiceClient orderServiceClient;

    public OrderCancelledInventoryHandler(ObjectMapper objectMapper, IdempotentConsumer idempotentConsumer,
                                          InventoryApplicationService inventoryApplicationService,
                                          InventoryRepository inventoryRepository,
                                          OrderServiceClient orderServiceClient) {
        super(objectMapper, idempotentConsumer);
        this.inventoryApplicationService = inventoryApplicationService;
        this.inventoryRepository = inventoryRepository;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    protected void handle(Envelope envelope, OrderCancelledEventPayload payload) {
        // 乱序裁决：以订单服务的真实状态为准（传输异常向上传播 → MQ 重试）
        String status = orderServiceClient.getStatus(payload.orderId());
        if (RELEASE_FORBIDDEN_STATUSES.contains(status)) {
            log.warn("ORDER_CANCELLED 乱序到达：订单真实状态={}，跳过释放防误扣 eventId={}, orderNo={}",
                    status, envelope.getEventId(), payload.orderNo());
            markSkipped(envelope.getEventId(), consumerGroup());
            return;
        }
        if (!ORDER_CANCELLED.equals(status)) {
            // 状态未知/缺失：保守不操作，抛错交重试，最终超限 DLQ
            throw new IllegalStateException("订单状态不满足取消释放条件: orderId=" + payload.orderId()
                    + ", status=" + status);
        }

        List<String> reservationIds = inventoryRepository.findReservationIdsByOrderNo(payload.orderNo());
        for (String reservationId : reservationIds) {
            try {
                // 应用服务对 RELEASED 终态幂等返回，重复投递安全
                inventoryApplicationService.release(new ReleaseCommand(reservationId));
            } catch (RuntimeException ex) {
                log.warn("消费释放预留失败，先登记补偿再交重试 orderNo={}, reservationId={}",
                        payload.orderNo(), reservationId, ex);
                registerReleaseCompensation(payload, reservationId, ex);
                throw ex;
            }
        }
    }

    /** 失败行登记 INVENTORY_RELEASE 补偿；补偿登记本身失败不掩盖原始异常。 */
    private void registerReleaseCompensation(OrderCancelledEventPayload payload, String reservationId,
                                             RuntimeException cause) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(reservationId).orElse(null);
        List<Line> lines;
        if (reservation != null) {
            lines = List.of(new Line(reservation.getSkuId(), (int) reservation.getQuantity(), reservationId));
        } else {
            long skuId = parseSkuId(reservationId);
            lines = List.of(new Line(skuId, 0, reservationId));
        }
        try {
            orderServiceClient.registerCompensation(new CompensationRequest(
                    payload.orderId(), payload.orderNo(), COMPENSATION_RELEASE,
                    "消费 ORDER_CANCELLED 释放预留失败: " + cause.getMessage(), lines));
        } catch (RuntimeException compensationError) {
            log.error("登记释放补偿失败 orderNo={}, reservationId={}",
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
