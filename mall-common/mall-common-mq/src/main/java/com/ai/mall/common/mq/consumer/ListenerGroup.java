package com.ai.mall.common.mq.consumer;

import java.util.Map;

/**
 * 合并后的监听器组：同一（消费组, topic）下的多 tag handler 共用一个物理消费者。
 *
 * @param consumerGroup 消费组名
 * @param topic         订阅 topic
 * @param subscription  合并订阅表达式（tag 以 || 连接）
 * @param handlersByTag tag → handler 路由表（收到消息后按消息 tag 分发）
 */
public record ListenerGroup(String consumerGroup, String topic, String subscription,
                            Map<String, InternalHandlerAdapter> handlersByTag) {
}
