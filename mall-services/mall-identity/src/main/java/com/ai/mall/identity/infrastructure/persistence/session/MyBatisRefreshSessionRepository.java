package com.ai.mall.identity.infrastructure.persistence.session;

import com.ai.mall.identity.domain.model.session.RefreshSession;
import com.ai.mall.identity.domain.repository.RefreshSessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisRefreshSessionRepository implements RefreshSessionRepository {
    private final RefreshSessionMapper mapper;

    public MyBatisRefreshSessionRepository(RefreshSessionMapper mapper) { this.mapper = mapper; }
    @Override public Optional<RefreshSession> findByDigest(String digest) { return Optional.ofNullable(mapper.find(digest)).map(p -> new RefreshSession(p.digest(),p.familyId(),p.adminId(),p.authVersion(),p.expiresAt(),p.usedAt(),p.revokedAt())); }
    @Override public void save(RefreshSession token) { mapper.save(new RefreshSessionPo(token.digest(),token.familyId(),token.adminId(),token.authVersion(),token.expiresAt(),token.usedAt(),token.revokedAt())); }
    @Override public boolean consume(String digest, long authVersion, Instant usedAt) { return mapper.consume(digest, authVersion, usedAt) == 1; }
    @Override public void revokeFamily(String familyId, Instant revokedAt) { mapper.revokeFamily(familyId, revokedAt); }
    @Override public void revokeAllForAdmin(long adminId, Instant revokedAt) { mapper.revokeAll(adminId, revokedAt); }
}
