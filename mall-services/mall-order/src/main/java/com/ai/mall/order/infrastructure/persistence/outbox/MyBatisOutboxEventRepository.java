package com.ai.mall.order.infrastructure.persistence.outbox;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.ai.mall.order.domain.outbox.OutboxStatus;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Outbox 事件仓储 MyBatis-Plus 实现（CHG-0025 M7 STORY-009-02-01）。 */
@Repository
public class MyBatisOutboxEventRepository implements OutboxEventRepository {

    private final OutboxEventMapper mapper;

    public MyBatisOutboxEventRepository(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OutboxEvent event) {
        OutboxEventPo po = toPo(event);
        mapper.insert(po);
        event.assignPersistedId(po.getId());
    }

    @Override
    public List<OutboxEvent> findPendingDue(int limit, Instant now) {
        return mapper.selectList(lambdaQuery(OutboxEventPo.class)
                        .eq(OutboxEventPo::getStatus, OutboxStatus.PENDING.name())
                        .and(w -> w.isNull(OutboxEventPo::getNextRetryAt).or().le(OutboxEventPo::getNextRetryAt, now))
                        .orderByAsc(OutboxEventPo::getAggregateId)
                        .orderByAsc(OutboxEventPo::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public int claim(Long id, Instant sentAt) {
        return mapper.claim(id, sentAt);
    }

    @Override
    public int markSent(Long id) {
        return mapper.markSent(id);
    }

    @Override
    public int markFailed(Long id, String lastError) {
        return mapper.markFailed(id, truncate(lastError));
    }

    @Override
    public int requeueWithBackoff(Long id, String lastError, Instant nextRetryAt, int retryCount) {
        return mapper.requeueWithBackoff(id, truncate(lastError), nextRetryAt, retryCount);
    }

    @Override
    public int resetForRetry(Long id) {
        return mapper.resetForRetry(id);
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public OutboxPage page(String status, String eventType, String aggregateId, int page, int size) {
        var wrapper = lambdaQuery(OutboxEventPo.class)
                .eq(status != null && !status.isBlank(), OutboxEventPo::getStatus, status)
                .eq(eventType != null && !eventType.isBlank(), OutboxEventPo::getEventType, eventType)
                .eq(aggregateId != null && !aggregateId.isBlank(), OutboxEventPo::getAggregateId, aggregateId)
                .orderByDesc(OutboxEventPo::getCreatedAt);
        Page<OutboxEventPo> poPage = mapper.selectPage(new Page<>(page, size), wrapper);
        List<OutboxEvent> records = poPage.getRecords().stream().map(this::toDomain).toList();
        return new OutboxPage(records, poPage.getTotal(), page, size);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 512 ? error.substring(0, 512) : error;
    }

    private OutboxEventPo toPo(OutboxEvent domain) {
        OutboxEventPo po = new OutboxEventPo();
        po.setId(domain.getId());
        po.setAggregateId(domain.getAggregateId());
        po.setEventType(domain.getEventType());
        po.setPayload(domain.getPayload());
        po.setStatus(domain.getStatus().name());
        po.setRetryCount(domain.getRetryCount());
        po.setNextRetryAt(domain.getNextRetryAt());
        po.setTraceId(domain.getTraceId());
        po.setLastError(domain.getLastError());
        po.setCreatedAt(domain.getCreatedAt());
        po.setSentAt(domain.getSentAt());
        return po;
    }

    private OutboxEvent toDomain(OutboxEventPo po) {
        return OutboxEvent.reconstitute(po.getId(), po.getAggregateId(), po.getEventType(), po.getPayload(),
                OutboxStatus.valueOf(po.getStatus()),
                po.getRetryCount() == null ? 0 : po.getRetryCount(),
                po.getNextRetryAt(), po.getTraceId(), po.getLastError(), po.getCreatedAt(), po.getSentAt());
    }
}
