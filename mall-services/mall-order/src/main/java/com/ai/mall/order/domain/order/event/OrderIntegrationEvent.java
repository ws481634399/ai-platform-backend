package com.ai.mall.order.domain.order.event;

/**
 * 订单集成事件（领域事件标记）。
 *
 * <p>仅承载类型，不做字段快照：Payload 组装在事务 flush 时从订单聚合当前状态读取，
 * 避免事件字段与聚合状态双份维护产生不一致。
 *
 * @param type 事件类型
 */
public record OrderIntegrationEvent(OrderIntegrationEventType type) {

    public OrderIntegrationEvent {
        if (type == null) {
            throw new IllegalArgumentException("事件类型不能为空");
        }
    }
}
