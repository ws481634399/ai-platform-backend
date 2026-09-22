package com.ai.mall.common.mq.producer;

import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 基于 RocketMQ 原生 Producer 的集成事件发送实现。
 * <p>traceId 策略：Envelope.traceId 为空时从 MDC 读取当前链路值，仍缺失则自动生成（不阻断发送）。</p>
 */
public class RocketMQEnvelopeProducer implements IntegrationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQEnvelopeProducer.class);

    /** RocketMQ 支持的最大延迟级别（1s~2h 共 18 级） */
    private static final int MAX_DELAY_LEVEL = 18;

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;

    public RocketMQEnvelopeProducer(DefaultMQProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendSync(String topic, Envelope envelope) {
        SendResult result = sendQuietly(topic, envelope, 0);
        log.info("集成事件同步发送完成 topic={} eventId={} msgId={} status={}",
                topic, envelope.getEventId(), result.getMsgId(), result.getSendStatus());
    }

    @Override
    public void sendAsync(String topic, Envelope envelope, SendCallback callback) {
        try {
            producer.send(buildMessage(topic, envelope), new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.info("集成事件异步发送完成 topic={} eventId={} msgId={} status={}",
                            topic, envelope.getEventId(), result.getMsgId(), result.getSendStatus());
                    if (callback != null) {
                        callback.onSuccess(result);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error("集成事件异步发送失败 topic={} eventId={}", topic, envelope.getEventId(), e);
                    if (callback != null) {
                        callback.onException(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("集成事件异步发送提交失败 topic={} eventId={}", topic, envelope.getEventId(), e);
            throw new IllegalStateException("异步发送提交失败: " + envelope.getEventId(), e);
        }
    }

    @Override
    public void sendDelay(String topic, Envelope envelope, int delayLevel) {
        // 延迟级别参数校验：不静默修正，防止调用方算错级别后静默变形
        if (delayLevel < 1 || delayLevel > MAX_DELAY_LEVEL) {
            throw new IllegalArgumentException("delayLevel 必须在 1~" + MAX_DELAY_LEVEL + " 区间，实际=" + delayLevel);
        }
        SendResult result = sendQuietly(topic, envelope, delayLevel);
        log.info("集成事件延迟发送完成 topic={} eventId={} delayLevel={} msgId={} status={}",
                topic, envelope.getEventId(), delayLevel, result.getMsgId(), result.getSendStatus());
    }

    private SendResult sendQuietly(String topic, Envelope envelope, int delayLevel) {
        try {
            Message message = buildMessage(topic, envelope);
            if (delayLevel > 0) {
                // RocketMQ 延迟消息正确姿势：level 打在消息属性上（客户端无 send(msg, delayLevel) 重载，
                // 误用 send(msg, timeout) 会把级别当成发送超时毫秒数）
                message.setDelayTimeLevel(delayLevel);
            }
            return producer.send(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送被中断: " + envelope.getEventId(), e);
        } catch (Exception e) {
            // 失败上抛：由调用方（Outbox 投递任务/同步降级路径）决定退避与补偿
            throw new IllegalStateException("集成事件发送失败: eventId=" + envelope.getEventId(), e);
        }
    }

    /**
     * 统一消息构造：tag=eventType、keys=eventId、body=Envelope JSON；
     * traceId 缺失时先取 MDC 再生成，保证全链路可追踪。
     */
    Message buildMessage(String topic, Envelope envelope) {
        Envelope effective = withTraceId(envelope);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(effective);
        } catch (Exception e) {
            throw new IllegalStateException("Envelope 序列化失败: " + envelope.getEventId(), e);
        }
        return new Message(topic, effective.getEventType(), effective.getEventId(), body);
    }

    private Envelope withTraceId(Envelope envelope) {
        if (envelope.getTraceId() != null && !envelope.getTraceId().isBlank()) {
            return envelope;
        }
        String mdcTraceId = MDC.get("traceId");
        String traceId = mdcTraceId != null && !mdcTraceId.isBlank()
                ? mdcTraceId
                : UUID.randomUUID().toString().replace("-", "");
        return Envelope.builder()
                .eventId(envelope.getEventId())
                .eventType(envelope.getEventType())
                .eventVersion(envelope.getEventVersion())
                .occurredAt(envelope.getOccurredAt())
                .producer(envelope.getProducer())
                .traceId(traceId)
                .payload(envelope.getPayload())
                .build();
    }
}
