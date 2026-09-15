package com.ai.mall.identity.infrastructure.persistence.member;

import com.ai.mall.identity.domain.model.member.MemberRefreshSession;
import com.ai.mall.identity.domain.repository.MemberRefreshSessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 会员刷新令牌仓储 MyBatis 实现（CHG-0016）。
 */
@Repository
public class MyBatisMemberRefreshSessionRepository implements MemberRefreshSessionRepository {

    private final MemberRefreshSessionMapper mapper;

    public MyBatisMemberRefreshSessionRepository(MemberRefreshSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MemberRefreshSession> findByDigest(String digest) {
        return Optional.ofNullable(mapper.find(digest)).map(po -> new MemberRefreshSession(
                po.digest(), po.familyId(), po.memberId(), po.authVersion(),
                po.expiresAt(), po.usedAt(), po.revokedAt()));
    }

    @Override
    public void save(MemberRefreshSession token) {
        mapper.save(new MemberRefreshSessionPo(token.digest(), token.familyId(), token.memberId(),
                token.authVersion(), token.expiresAt(), token.usedAt(), token.revokedAt()));
    }

    @Override
    public boolean consume(String digest, long authVersion, Instant usedAt) {
        return mapper.consume(digest, authVersion, usedAt) > 0;
    }

    /**
     * 重放/异常撤销必须独立提交（REQUIRES_NEW）：rotate 在撤销后抛 401，外层事务随之回滚，
     * 若同事务则整族撤销会被一并回滚，重放检测失去意义。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void revokeFamily(String familyId, Instant revokedAt) {
        mapper.revokeFamily(familyId, revokedAt);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void revokeAllForMember(long memberId, Instant revokedAt) {
        mapper.revokeAllForMember(memberId, revokedAt);
    }
}
