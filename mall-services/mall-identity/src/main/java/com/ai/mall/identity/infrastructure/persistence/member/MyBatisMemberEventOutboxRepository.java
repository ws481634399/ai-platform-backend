package com.ai.mall.identity.infrastructure.persistence.member;

import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;
import com.ai.mall.identity.domain.model.member.PendingMemberEvent;
import com.ai.mall.identity.domain.repository.MemberEventOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 会员事件 outbox 仓储 MyBatis 实现（CHG-0016）。
 *
 * <p>payload_json 以独立 {@link ObjectMapper}（注册 JavaTimeModule，ISO-8601 时刻）
 * 序列化 MemberRegistered 事件；载荷内 memberId 按事件契约以字符串承载。
 */
@Repository
public class MyBatisMemberEventOutboxRepository implements MemberEventOutboxRepository {

    /** 事件类型 SSOT：MemberRegistered v1。 */
    static final String EVENT_TYPE_MEMBER_PROVISIONED = "MEMBER_PROVISIONED";

    private static final String STATUS_PENDING = "PENDING";

    private final MemberEventOutboxMapper mapper;
    private final ObjectMapper payloadMapper;

    public MyBatisMemberEventOutboxRepository(MemberEventOutboxMapper mapper) {
        this.mapper = mapper;
        // 独立 mapper，避免对 Web 侧全局 ObjectMapper 配置产生隐式依赖
        this.payloadMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.payloadMapper.setDateFormat(new StdDateFormat());
    }

    @Override
    public void append(MemberRegisteredEvent event) {
        MemberEventOutboxPo po = new MemberEventOutboxPo();
        po.setEventId(event.eventId());
        po.setEventType(EVENT_TYPE_MEMBER_PROVISIONED);
        po.setMemberId(event.memberId());
        po.setPayloadJson(writePayload(event));
        po.setStatus(STATUS_PENDING);
        po.setRetryCount(0);
        po.setCreatedAt(event.occurredAt());
        mapper.insert(po);
    }

    @Override
    public List<PendingMemberEvent> findPending(int limit) {
        return mapper.findPending(limit).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PendingMemberEvent> findPendingByEventId(String eventId) {
        return Optional.ofNullable(mapper.findPendingByEventId(eventId)).map(this::toDomain);
    }

    @Override
    public void markDone(String eventId, Instant sentAt) {
        mapper.markDone(eventId, sentAt);
    }

    @Override
    public void advanceRetry(String eventId, int retryCount) {
        mapper.advanceRetry(eventId, retryCount);
    }

    private String writePayload(MemberRegisteredEvent event) {
        // 显式 LinkedHashMap 固定字段顺序；memberId 转字符串（事件契约 SSOT）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("memberId", Long.toString(event.memberId()));
        payload.put("username", event.username());
        payload.put("nickname", event.nickname());
        payload.put("occurredAt", event.occurredAt());
        try {
            return payloadMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("member event payload serialization failed: " + event.eventId(), ex);
        }
    }

    private PendingMemberEvent toDomain(MemberEventOutboxPo po) {
        try {
            JsonNode node = payloadMapper.readTree(po.getPayloadJson());
            long memberId = node.get("memberId").asLong();
            Instant occurredAt = payloadMapper.convertValue(node.get("occurredAt"), Instant.class);
            MemberRegisteredEvent event = MemberRegisteredEvent.reconstitute(
                    po.getEventId(), memberId, node.get("username").asText(),
                    node.get("nickname").asText(), occurredAt);
            return new PendingMemberEvent(event, po.getRetryCount());
        } catch (Exception ex) {
            throw new IllegalStateException("member event payload deserialization failed: " + po.getEventId(), ex);
        }
    }
}
