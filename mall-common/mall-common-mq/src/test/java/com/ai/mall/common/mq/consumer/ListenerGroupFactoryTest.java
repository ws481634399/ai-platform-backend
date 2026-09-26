package com.ai.mall.common.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 同组 handler 分组逻辑测试：RocketMQ 要求同一消费组内所有消费者订阅完全一致，
 * 多 tag handler 必须合并为单个消费者（订阅表达式 A || B）+ 内部按 tag 分发。
 */
class ListenerGroupFactoryTest {

    private static InternalHandlerAdapter adapter() {
        return (msgs, context) -> ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    @Test
    @DisplayName("同组同 topic、两个不同 tag → 合并为 1 个组，订阅表达式 TAG_A || TAG_B，按 tag 保留两个 handler")
    void sameGroupDifferentTags_mergedWithCombinedSubscription() {
        List<ListenerGroup> groups = ListenerGroupFactory.group(List.of(
                new HandlerDescriptor(adapter(), "inv-group", "aimall-order-events", "ORDER_CANCELLED"),
                new HandlerDescriptor(adapter(), "inv-group", "aimall-order-events", "PAYMENT_SUCCEEDED")));

        assertThat(groups).hasSize(1);
        ListenerGroup group = groups.get(0);
        assertThat(group.consumerGroup()).isEqualTo("inv-group");
        assertThat(group.topic()).isEqualTo("aimall-order-events");
        assertThat(group.subscription()).isEqualTo("ORDER_CANCELLED || PAYMENT_SUCCEEDED");
        assertThat(group.handlersByTag()).hasSize(2).containsKeys("ORDER_CANCELLED", "PAYMENT_SUCCEEDED");
    }

    @Test
    @DisplayName("不同消费组 → 各自独立分组；单 handler 订阅表达式即其 tag")
    void differentGroups_separated() {
        List<ListenerGroup> groups = ListenerGroupFactory.group(List.of(
                new HandlerDescriptor(adapter(), "inv-group", "aimall-order-events", "ORDER_CANCELLED"),
                new HandlerDescriptor(adapter(), "other-group", "aimall-order-events", "ORDER_CANCELLED")));

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(g -> assertThat(g.handlersByTag()).hasSize(1));
        assertThat(groups).allSatisfy(g -> assertThat(g.subscription()).isEqualTo("ORDER_CANCELLED"));
    }

    @Test
    @DisplayName("同组同 topic 出现重复 tag → 快速失败（禁止 handler 间路由歧义）")
    void duplicateTagInSameGroup_failsFast() {
        assertThatThrownBy(() -> ListenerGroupFactory.group(List.of(
                new HandlerDescriptor(adapter(), "inv-group", "aimall-order-events", "ORDER_CANCELLED"),
                new HandlerDescriptor(adapter(), "inv-group", "aimall-order-events", "ORDER_CANCELLED"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORDER_CANCELLED");
    }
}
