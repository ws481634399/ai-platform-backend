package com.ai.mall.identity.domain.repository;

import com.ai.mall.identity.domain.model.session.RefreshSession;

import java.time.Instant;
import java.util.Optional;

public interface RefreshSessionRepository {
    Optional<RefreshSession> findByDigest(String digest);

    void save(RefreshSession token);

    boolean consume(String digest, long authVersion, Instant usedAt);

    void revokeFamily(String familyId, Instant revokedAt);

    void revokeAllForAdmin(long adminId, Instant revokedAt);
}
