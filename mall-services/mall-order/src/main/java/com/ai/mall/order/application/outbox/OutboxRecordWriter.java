package com.ai.mall.order.application.outbox;

import com.ai.mall.event.Envelope;
import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 记录写入器：在调用方事务内持久化事件。
 *
 * <p>写入失败抛异常，由调用方事务回滚——保证业务数据与 Outbox 记录原子提交（AC-010）。</p>
 */
@Component
public class OutboxRecordWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxRecordWriter.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxRecordWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 在调用方事务内追加一条 Outbox 记录。
     *
     * @param aggregateId 聚合根 ID（如 orderId）
     * @param eventType   事件类型（=消息 Tag）
     * @param envelope    完整事件信封（payload 列存其 JSON）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateId, String eventType, Envelope envelope) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox 事件序列化失败: " + eventType, e);
        }
        OutboxEvent event = new OutboxEvent(aggregateId, eventType, payloadJson, envelope.getTraceId());
        repository.save(event);
        log.debug("Outbox 记录已写入: eventId={}, aggregateId={}, eventType={}",
                envelope.getEventId(), aggregateId, eventType);
    }
}
