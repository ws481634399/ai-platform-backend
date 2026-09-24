package com.ai.mall.order.application.compensation;

import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 订单自动取消补偿执行器（CHG-0025 STORY-009-05-01）。
 *
 * <p>补偿触发来源：延迟回查消费者调 systemCancel 失败（非状态冲突）时登记本任务（AC-035）；
 * 执行仍走 {@link OrderCancelService#systemCancel}——CAS + ORDER_CANCELLED Outbox + 库存释放全链路，
 * CANCELLED 幂等返回视为成功；状态冲突（已支付等）抛 BusinessException 由调度记退避。
 */
@Component
public class OrderAutoCancelCompensationHandler implements CompensationActionHandler {

    private final OrderCancelService orderCancelService;
    private final ObjectMapper objectMapper;

    public OrderAutoCancelCompensationHandler(OrderCancelService orderCancelService, ObjectMapper objectMapper) {
        this.orderCancelService = orderCancelService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String operation) {
        return CompensationTask.OP_AUTO_CANCEL_ORDER.equals(operation);
    }

    @Override
    public void handle(CompensationTask task) {
        OrderAutoCancelCompensationPayload payload;
        try {
            payload = objectMapper.readValue(task.payload(), OrderAutoCancelCompensationPayload.class);
        } catch (Exception ex) {
            // 载荷不可解析：重试无意义，但仍走有界重试（人工可见 FAILED_DEAD 与错误信息）
            throw new IllegalStateException("补偿载荷解析失败: " + ex.getMessage(), ex);
        }
        orderCancelService.systemCancel(payload.orderId(), "PAYMENT_TIMEOUT", "COMPENSATION");
    }
}
