package com.ai.mall.common.mq.consumer;

import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 消费容器适配桥：AutoConfiguration 通过该接口把 RocketMQ 推送回调
 * 接到 AbstractIntegrationHandler 统一处理链（避免向业务侧暴露容器细节）。
 */
public interface InternalHandlerAdapter {

    ConsumeConcurrentlyStatus processBatch(List<MessageExt> msgs, ConsumeConcurrentlyContext context);
}
