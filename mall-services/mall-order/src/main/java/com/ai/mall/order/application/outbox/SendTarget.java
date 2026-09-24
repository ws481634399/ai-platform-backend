package com.ai.mall.order.application.outbox;

/**
 * 事件投递目标：Topic + 延迟级别。
 *
 * @param topic      目标 Topic（EventTopics 常量）
 * @param delayLevel RocketMQ 延迟级别（0 表示即时同步发送，1~18 为延迟级别）
 */
public record SendTarget(String topic, int delayLevel) {

    public boolean isDelayed() {
        return delayLevel > 0;
    }
}
