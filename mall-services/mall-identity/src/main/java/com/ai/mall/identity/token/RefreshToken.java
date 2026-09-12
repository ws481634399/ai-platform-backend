package com.ai.mall.identity.token;

import java.time.Instant;

public record RefreshToken(
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

    public RefreshToken markUsed(Instant now) {
        return new RefreshToken(digest, familyId, adminId, authVersion, expiresAt, now, revokedAt);
    }

    public RefreshToken revoke(Instant now) {
        return new RefreshToken(digest, familyId, adminId, authVersion, expiresAt, usedAt, now);
    }
}
