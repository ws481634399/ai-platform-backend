package com.ai.mall.common.mq.consumer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 集成事件监听器组合注解：标注在 AbstractIntegrationHandler 子类上，
 * 由 mall-mq 自动装配为 RocketMQ Push 消费者（topic/Tag/消费组三要素集中声明）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IntegrationEventListener {

    /** 订阅 Topic（使用 EventTopics 常量） */
    String topic();

    /** 订阅 Tag 表达式（=eventType；多 Tag 用 || 连接） */
    String eventType();

    /** 消费者组（使用 ConsumerGroups 常量） */
    String consumerGroup();

    /** 消费端支持的最大事件版本：超过则拒绝（WARN+ACK，不按旧版本解析） */
    int maxSupportedVersion() default 1;
}
