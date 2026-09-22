package com.ai.mall.common.mq.config;

import com.ai.mall.common.mq.consumer.ConsumedEventRepository;
import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.mq.consumer.IntegrationEventListener;
import com.ai.mall.common.mq.producer.IntegrationEventProducer;
import com.ai.mall.common.mq.producer.RocketMQEnvelopeProducer;
import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RocketMQ 集成事件自动装配。
 * <p>开关语义：rocketmq.enabled=false（或缺省）时本装配整体不生效，
 * Producer/消费者容器/幂等组件均不创建，服务以同步路径运行（无 NoOp 半开态）。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@EnableConfigurationProperties(RocketMQProperties.class)
public class RocketMQAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RocketMQAutoConfiguration.class);

    @Bean(destroyMethod = "shutdown", initMethod = "start")
    public DefaultMQProducer integrationEventRocketMQProducer(RocketMQProperties properties) {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(properties.getSendMsgTimeout());
        producer.setRetryTimesWhenSendFailed(properties.getRetryTimesWhenSendFailed());
        producer.setRetryTimesWhenSendAsyncFailed(properties.getRetryTimesWhenSendAsyncFailed());
        return producer;
    }

    @Bean
    public IntegrationEventProducer integrationEventProducer(DefaultMQProducer producer,
                                                             ObjectMapper objectMapper) {
        return new RocketMQEnvelopeProducer(producer, objectMapper);
    }

    /** 消费幂等组件：依赖消费方数据库（fail-closed——无 JdbcTemplate 时不装配，防止无幂等消费） */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public IdempotentConsumer idempotentConsumer(JdbcTemplate jdbcTemplate,
                                                 RocketMQProperties properties) {
        return new ConsumedEventRepository(jdbcTemplate, properties.getIdempotentTable());
    }

    /**
     * 消费者容器注册器：扫描 @IntegrationEventListener handler Bean，
     * 每个声明创建一个 DefaultMQPushConsumer（生命周期随容器 start/stop）。
     */
    @Bean
    public SmartInitializingSingleton integrationListenerRegistrar(ApplicationContext applicationContext,
                                                                   RocketMQProperties properties) {
        return () -> {
            Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(IntegrationEventListener.class);
            List<DefaultMQPushConsumer> consumers = new ArrayList<>();
            handlers.values().stream()
                    .filter(Objects::nonNull)
                    .forEach(handlerBean -> {
                        Class<?> handlerClass = handlerBean.getClass();
                        IntegrationEventListener listener = handlerClass.getAnnotation(IntegrationEventListener.class);
                        if (listener == null) {
                            // CGLIB 代理场景下注解在超类
                            listener = handlerClass.getSuperclass() != null
                                    ? handlerClass.getSuperclass().getAnnotation(IntegrationEventListener.class)
                                    : null;
                        }
                        if (listener == null) {
                            throw new IllegalStateException(
                                    "Bean 缺少 @IntegrationEventListener 注解: " + handlerClass);
                        }
                        if (!(handlerBean instanceof com.ai.mall.common.mq.consumer.InternalHandlerAdapter adapter)) {
                            throw new IllegalStateException(
                                    "消费者 Bean 必须继承 AbstractIntegrationHandler: " + handlerClass);
                        }
                        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(listener.consumerGroup());
                        consumer.setNamesrvAddr(properties.getNameServer());
                        consumer.setInstanceName(listener.consumerGroup() + "-"
                                + listener.topic() + "-" + handlerClass.getSimpleName());
                        consumer.setConsumeThreadMin(properties.getConsumeThreadMin());
                        consumer.setConsumeThreadMax(properties.getConsumeThreadMax());
                        consumer.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
                        try {
                            consumer.subscribe(listener.topic(), listener.eventType());
                        } catch (Exception e) {
                            throw new IllegalStateException("订阅失败 topic=" + listener.topic(), e);
                        }
                        consumer.registerMessageListener(new ConsumeConcurrentlyContextAdapter(adapter));
                        consumers.add(consumer);
                        log.info("装配集成事件消费者 topic={} tag={} group={} handler={}",
                                listener.topic(), listener.eventType(), listener.consumerGroup(),
                                handlerClass.getSimpleName());
                    });

            // 容器关闭时停消费者；启动由 SmartLifecycle 语义延迟到单例就绪后
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                    consumers.forEach(DefaultMQPushConsumer::shutdown), "integration-consumer-shutdown"));
            for (DefaultMQPushConsumer consumer : consumers) {
                try {
                    consumer.start();
                } catch (Exception e) {
                    throw new IllegalStateException("消费者启动失败 group=" + consumer.getConsumerGroup(), e);
                }
            }
        };
    }

    /** MessageListenerConcurrently 适配：把 RocketMQ 回调接到 AbstractIntegrationHandler 处理链 */
    private record ConsumeConcurrentlyContextAdapter(
            com.ai.mall.common.mq.consumer.InternalHandlerAdapter adapter)
            implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                        ConsumeConcurrentlyContext context) {
            return adapter.processBatch(msgs, context);
        }
    }
}
