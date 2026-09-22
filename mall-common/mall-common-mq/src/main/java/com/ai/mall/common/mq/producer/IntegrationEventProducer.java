package com.ai.mall.common.mq.producer;

import com.ai.mall.event.Envelope;
import org.apache.rocketmq.client.producer.SendCallback;

/**
 * 集成事件生产者接口：三态发送（同步/异步/延迟）。
 * <p>消息构造统一约定：topic=EventTopics 常量、Tag=eventType、keys=eventId、body=Envelope JSON。</p>
 * <p>失败语义：sendSync/sendDelay 失败直接上抛（Outbox 投递任务捕获退避），本组件不吞异常。</p>
 */
public interface IntegrationEventProducer {

    /** 同步发送：Broker 确认后才返回；失败上抛 */
    void sendSync(String topic, Envelope envelope);

    /** 异步发送：结果经回调通知；发送阶段异常回调 onException */
    void sendAsync(String topic, Envelope envelope, SendCallback callback);

    /** 延迟发送：delayLevel 为 RocketMQ 延迟级别（1~18，向上取整由调用方换算）；失败上抛 */
    void sendDelay(String topic, Envelope envelope, int delayLevel);
}
