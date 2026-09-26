package com.ai.mall.common.mq.consumer;

import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 集成事件统一消费处理链（六步）：
 * ①版本裁决（超版 WARN+ACK 拒绝）→ ②TraceId 写 MDC（缺失自动生成）→ ③幂等占位（重复 ACK 跳过）
 * → ④业务 handle(payload) → ⑤结果回写（SUCCESS；SKIPPED 由子类显式调用）→ ⑥消费日志+清理 MDC。
 * <p>异常语义：handle 抛异常 → 删除幂等占位 → 返回 RECONSUME_LATER 交 RocketMQ 重试，
 * 超过 maxReconsumeTimes 进 DLQ（%DLQ%{consumerGroup}）。</p>
 *
 * @param <P> 业务负载类型（payload 包 DTO）
 */
public abstract class AbstractIntegrationHandler<P> implements InternalHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationHandler.class);

    /** MDC traceId 键：与 mall-common-web TraceConstants.MDC_KEY 同口径 */
    static final String MDC_TRACE_KEY = "traceId";

    protected final ObjectMapper objectMapper;
    protected final IdempotentConsumer idempotentConsumer;
    private final Class<P> payloadType;

    protected AbstractIntegrationHandler(ObjectMapper objectMapper, IdempotentConsumer idempotentConsumer) {
        this.objectMapper = objectMapper;
        this.idempotentConsumer = idempotentConsumer;
        this.payloadType = resolvePayloadType();
    }

    /** 子类业务处理：抛异常视为处理失败（删占位+重试） */
    protected abstract void handle(Envelope envelope, P payload) throws Exception;

    /** 乱序/非预期状态跳过：业务侧判断后显式调用，回写 SKIPPED 并告警 */
    protected final void markSkipped(String eventId, String consumerGroup) {
        idempotentConsumer.markResult(eventId, consumerGroup, IdempotentConsumer.Result.SKIPPED);
    }

    /** 单条消息处理入口：解析失败时尚未建立幂等占位，直接返回重试；其余路径在 finally 统一收尾 */
    final ConsumeConcurrentlyStatus processMessage(MessageExt messageExt) {
        long start = System.currentTimeMillis();
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    Envelope.class);
        } catch (Exception e) {
            // 解析失败与版本拒绝区分：前者按失败重试（保守不丢消息），后者 ACK+WARN
            log.error("集成事件解析失败 brokerMsgId={} retryTimes={}",
                    messageExt.getMsgId(), messageExt.getReconsumeTimes(), e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }

        // ① 版本裁决：超出支持版本 → WARN+ACK（不重试避免死循环，不按旧版本解析）
        IntegrationEventListener listener = getClass().getAnnotation(IntegrationEventListener.class);
        int maxVersion = listener != null ? listener.maxSupportedVersion() : Integer.MAX_VALUE;
        if (envelope.getEventVersion() > maxVersion) {
            log.warn("集成事件版本拒绝 ACK eventId={} eventVersion={} maxSupportedVersion={}",
                    envelope.getEventId(), envelope.getEventVersion(), maxVersion);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        String consumerGroup = listener != null ? listener.consumerGroup() : "unknown-group";
        // ② TraceId 写 MDC：缺失自动生成新 ID，不阻断消费
        String traceId = envelope.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_TRACE_KEY, traceId);
        IdempotentConsumer.TryResult tryResult = IdempotentConsumer.TryResult.FIRST_PROCESSED;
        try {
            // ③ 幂等占位（INSERT IGNORE；DB 异常直接上抛 fail-closed）
            tryResult = idempotentConsumer.tryConsume(envelope.getEventId(), consumerGroup,
                    envelope.getEventType(), aggregateId(envelope), traceId);
            if (tryResult == IdempotentConsumer.TryResult.DUPLICATE) {
                log.info("重复消息跳过 eventId={} consumerGroup={}", envelope.getEventId(), consumerGroup);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            // ④ 业务处理
            P payload = objectMapper.treeToValue(envelope.getPayload(), payloadType);
            handle(envelope, payload);
            // ⑤ 结果回写：仅当仍为 PROCESSING 时置 SUCCESS（SKIPPED 由子类在 handle 内显式标记，不得覆盖）
            idempotentConsumer.markSuccessIfProcessing(envelope.getEventId(), consumerGroup);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            // 异常路径：删除幂等占位（允许重试再处理）→ 交 RocketMQ 重试 → 超限 DLQ
            try {
                idempotentConsumer.deletePlaceholder(envelope.getEventId(), consumerGroup);
            } catch (Exception cleanupError) {
                log.error("幂等占位删除失败 eventId={} consumerGroup={}",
                        envelope.getEventId(), consumerGroup, cleanupError);
            }
            log.error("集成事件消费失败 eventId={} traceId={} consumerGroup={} retryTimes={}",
                    envelope.getEventId(), traceId, consumerGroup, messageExt.getReconsumeTimes(), e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        } finally {
            // ⑥ 消费日志（eventId/耗时/结果）+ 清理 MDC
            log.info("集成事件消费结束 eventId={} eventType={} result={} costMs={}",
                    envelope.getEventId(), envelope.getEventType(), tryResult,
                    System.currentTimeMillis() - start);
            MDC.remove(MDC_TRACE_KEY);
        }
    }

    /** 乱序裁决回查用的聚合 ID（默认 payload 的 orderId 字段，子类可覆写） */
    protected String aggregateId(Envelope envelope) {
        return envelope.getPayload().hasNonNull("orderId")
                ? envelope.getPayload().get("orderId").asText()
                : null;
    }

    @SuppressWarnings("unchecked")
    private Class<P> resolvePayloadType() {
        // 沿父类链向上找到 AbstractIntegrationHandler 的参数化类型（支持中间抽象层）
        Class<?> current = getClass();
        while (current != null && current != Object.class) {
            Type superType = current.getGenericSuperclass();
            if (superType instanceof ParameterizedType parameterized
                    && parameterized.getRawType() == AbstractIntegrationHandler.class) {
                return (Class<P>) parameterized.getActualTypeArguments()[0];
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("AbstractIntegrationHandler 子类必须显式声明负载泛型");
    }

    /** 供容器注册器批量处理同批消息 */
    @Override
    public ConsumeConcurrentlyStatus processBatch(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt messageExt : msgs) {
            if (processMessage(messageExt) == ConsumeConcurrentlyStatus.RECONSUME_LATER) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
