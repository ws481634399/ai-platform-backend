package com.ai.mall.common.mq.consumer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监听器分组工厂：按（消费组, topic）合并 handler。
 * <p>RocketMQ 约束：同一消费组内每个物理消费者的订阅关系必须完全一致，否则 broker
 * 告警 "Different subscription in the same group" 且 tag 过滤/队列分配错乱。
 * 因此同组多 tag handler 合并为一个物理消费者：订阅表达式 {@code tagA || tagB}，
 * 消息到达后按 tag 内部分发。</p>
 */
public final class ListenerGroupFactory {

    private ListenerGroupFactory() {
    }

    public static List<ListenerGroup> group(List<HandlerDescriptor> descriptors) {
        // 以 "消费组|topic" 归并；LinkedHashMap 保持注册顺序，便于日志稳定
        Map<String, ListenerGroupBuilder> merged = new LinkedHashMap<>();
        for (HandlerDescriptor descriptor : descriptors) {
            String key = descriptor.consumerGroup() + "|" + descriptor.topic();
            merged.computeIfAbsent(key,
                    k -> new ListenerGroupBuilder(descriptor.consumerGroup(), descriptor.topic()))
                    .add(descriptor.tag(), descriptor.adapter());
        }
        List<ListenerGroup> groups = new ArrayList<>(merged.size());
        for (ListenerGroupBuilder builder : merged.values()) {
            groups.add(builder.build());
        }
        return groups;
    }

    /** 单个（消费组, topic）的增量构建器 */
    private static final class ListenerGroupBuilder {

        private final String consumerGroup;
        private final String topic;
        private final List<String> tags = new ArrayList<>();
        private final Map<String, InternalHandlerAdapter> handlersByTag = new LinkedHashMap<>();

        private ListenerGroupBuilder(String consumerGroup, String topic) {
            this.consumerGroup = consumerGroup;
            this.topic = topic;
        }

        private void add(String tag, InternalHandlerAdapter adapter) {
            if (handlersByTag.containsKey(tag)) {
                // 同组同 topic 重复 tag 会造成分发歧义，快速失败暴露配置问题
                throw new IllegalStateException(
                        "同一消费组/topic 下存在重复 tag: group=" + consumerGroup
                                + " topic=" + topic + " tag=" + tag);
            }
            tags.add(tag);
            handlersByTag.put(tag, adapter);
        }

        private ListenerGroup build() {
            String subscription = String.join(" || ", tags);
            return new ListenerGroup(consumerGroup, topic, subscription, Map.copyOf(handlersByTag));
        }
    }
}
