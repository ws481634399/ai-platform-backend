package com.ai.mall.order.application.outbox;

import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 事件类型 → 投递目标 路由表。
 *
 * <p>默认注册订单四事件到 aimall-order-events（即时发送）；STORY-009-04-01 通过
 * {@link #register(String, SendTarget)} 扩展 PAYMENT_TIMEOUT_CHECK 到 aimall-order-delay。</p>
 */
@Component
public class EventRouter {

    private final Map<String, SendTarget> routes = new HashMap<>();

    @PostConstruct
    void initDefaultRoutes() {
        SendTarget orderEvents = new SendTarget(EventTopics.AIMALL_ORDER_EVENTS, 0);
        register(EventTags.ORDER_CREATED, orderEvents);
        register(EventTags.PAYMENT_SUCCEEDED, orderEvents);
        register(EventTags.ORDER_CANCELLED, orderEvents);
        register(EventTags.ORDER_COMPLETED, orderEvents);
    }

    /** 注册/覆盖路由（供后续 Story 扩展延迟消息等）。 */
    public void register(String eventType, SendTarget target) {
        routes.put(eventType, target);
    }

    /**
     * 按事件类型路由。
     *
     * @throws IllegalArgumentException 未知 eventType（配置错误，直接 FAILED 不重试）
     */
    public SendTarget route(String eventType) {
        SendTarget target = routes.get(eventType);
        if (target == null) {
            throw new IllegalArgumentException("未知事件类型，无投递路由: " + eventType);
        }
        return target;
    }
}
