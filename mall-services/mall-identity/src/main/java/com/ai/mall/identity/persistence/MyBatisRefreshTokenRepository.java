package com.ai.mall.identity.persistence;

import com.ai.mall.identity.token.RefreshToken;
import com.ai.mall.identity.token.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisRefreshTokenRepository implements RefreshTokenRepository {
    private final RefreshTokenMapper mapper;

    public MyBatisRefreshTokenRepository(RefreshTokenMapper mapper) { this.mapper = mapper; }
    @Override public Optional<RefreshToken> findByDigest(String digest) { return Optional.ofNullable(mapper.find(digest)); }
    @Override public void save(RefreshToken token) { mapper.save(token); }
    @Override public boolean consume(String digest, long authVersion, Instant usedAt) { return mapper.consume(digest, authVersion, usedAt) == 1; }
    @Override public void revokeFamily(String familyId, Instant revokedAt) { mapper.revokeFamily(familyId, revokedAt); }
    @Override public void revokeAllForAdmin(long adminId, Instant revokedAt) { mapper.revokeAll(adminId, revokedAt); }
}
