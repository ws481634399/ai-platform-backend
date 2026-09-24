package com.ai.mall.order.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EventRouter 路由表单测（TC-002）。 */
class EventRouterTest {

    private final EventRouter router;

    EventRouterTest() {
        router = new EventRouter();
        router.initDefaultRoutes(); // @PostConstruct 仅在 Spring 容器中触发，单测显式调用
    }

    @Test
    @DisplayName("订单四事件默认路由到 aimall-order-events 即时发送")
    void defaultOrderEventRoutes() {
        for (String tag : new String[]{EventTags.ORDER_CREATED, EventTags.PAYMENT_SUCCEEDED,
                EventTags.ORDER_CANCELLED, EventTags.ORDER_COMPLETED}) {
            SendTarget target = router.route(tag);
            assertThat(target.topic()).isEqualTo(EventTopics.AIMALL_ORDER_EVENTS);
            assertThat(target.delayLevel()).isZero();
            assertThat(target.isDelayed()).isFalse();
        }
    }

    @Test
    @DisplayName("未知 eventType 抛出 IllegalArgumentException（配置错误，直接 FAILED 不重试）")
    void unknownEventTypeThrows() {
        assertThatThrownBy(() -> router.route("UNKNOWN_EVENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_EVENT");
    }

    @Test
    @DisplayName("register 可扩展路由（供 STORY-009-04-01 延迟消息）")
    void registerExtendsRoute() {
        router.register(EventTags.PAYMENT_TIMEOUT_CHECK, new SendTarget(EventTopics.AIMALL_ORDER_DELAY, 9));
        SendTarget target = router.route(EventTags.PAYMENT_TIMEOUT_CHECK);
        assertThat(target.topic()).isEqualTo(EventTopics.AIMALL_ORDER_DELAY);
        assertThat(target.delayLevel()).isEqualTo(9);
        assertThat(target.isDelayed()).isTrue();
    }
}
