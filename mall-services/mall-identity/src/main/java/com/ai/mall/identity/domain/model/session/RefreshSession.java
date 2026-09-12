package com.ai.mall.identity.domain.model.session;

import java.time.Instant;

public record RefreshSession(
        String digest,
        String familyId,
        long adminId,
        long authVersion,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt) {

    public boolean isActive(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public RefreshSession markUsed(Instant now) {
        return new RefreshSession(digest, familyId, adminId, authVersion, expiresAt, now, revokedAt);
    }

    public RefreshSession revoke(Instant now) {
        return new RefreshSession(digest, familyId, adminId, authVersion, expiresAt, usedAt, now);
    }
}
