package com.ai.mall.common.mq.consumer;

/**
 * 事件处理器描述：handler Bean 与其声明的订阅坐标（消费组/topic/tag）。
 *
 * @param adapter       处理器适配入口
 * @param consumerGroup 消费组名
 * @param topic         订阅 topic
 * @param tag           订阅 tag（即事件类型）
 */
public record HandlerDescriptor(InternalHandlerAdapter adapter, String consumerGroup,
                                String topic, String tag) {
}
