package com.ai.mall.identity.infrastructure.persistence.member;

import java.time.Instant;

/**
 * member_refresh_token 行映射（CHG-0016）。
 */
public record MemberRefreshSessionPo(String digest, String familyId, long memberId, long authVersion,
                                     Instant expiresAt, Instant usedAt, Instant revokedAt) {
}
