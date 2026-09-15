package com.ai.mall.identity.domain.model.member;

import java.time.Instant;

/**
 * 会员刷新令牌会话（CHG-0016 / STORY-003-01-01-02）。
 *
 * <p>与 ADMIN 的 {@code RefreshSession} 同构但物理隔离：字段以 memberId 命名，
 * 行落在 member_refresh_token 表。三态判定：usedAt/revokedAt 均空且未过期才是 ACTIVE。
 */
public record MemberRefreshSession(
        String digest,
        String familyId,
        long memberId,
        long authVersion,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt) {

    /** ACTIVE：未旋转消费、未撤销、未过期。 */
    public boolean isActive(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
}
