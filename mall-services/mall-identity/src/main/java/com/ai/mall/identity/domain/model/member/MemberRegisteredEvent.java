package com.ai.mall.identity.domain.model.member;

import java.time.Instant;
import java.util.UUID;

/**
 * MemberRegistered v1 领域事件（CHG-0016）：会员注册成功后向 mall-member 开通档案的逻辑载体。
 *
 * <p>事件契约 SSOT（requirement-design §5.1）：{eventId(UUID), memberId, username, nickname, occurredAt}，
 * at-least-once 投递 + 消费端 eventId/memberId 双幂等；M7 替换为 MQ 时消费侧契约不变。
 * 事件载荷对外/跨服务传输时 memberId 一律字符串，避免 JS 精度问题。
 *
 * @param eventId    事件唯一 ID（UUID，消费端幂等键之一）
 * @param memberId   会员 ID（与 member_user.id 一致）
 * @param username   注册用户名（原始大小写）
 * @param nickname   默认昵称（"会员"+memberId 后 6 位）
 * @param occurredAt 事件发生时刻
 */
public record MemberRegisteredEvent(String eventId, long memberId, String username, String nickname,
                                    Instant occurredAt) {

    /** 注册事务内创建新事件：eventId 随机 UUID。 */
    public static MemberRegisteredEvent create(long memberId, String username, String nickname, Instant occurredAt) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        return new MemberRegisteredEvent(UUID.randomUUID().toString(), memberId, username, nickname, occurredAt);
    }

    /** 持久化/outbox 载荷重建（eventId 已存在）。 */
    public static MemberRegisteredEvent reconstitute(String eventId, long memberId, String username,
                                                     String nickname, Instant occurredAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        return new MemberRegisteredEvent(eventId, memberId, username, nickname, occurredAt);
    }
}
