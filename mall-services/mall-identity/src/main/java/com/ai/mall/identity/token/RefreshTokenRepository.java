package com.ai.mall.identity.token;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByDigest(String digest);

    void save(RefreshToken token);

    boolean consume(String digest, long authVersion, Instant usedAt);

    void revokeFamily(String familyId, Instant revokedAt);

    void revokeAllForAdmin(long adminId, Instant revokedAt);
}
