package com.ai.mall.identity.domain.repository;

import com.ai.mall.identity.domain.model.member.MemberRefreshSession;
import java.time.Instant;
import java.util.Optional;

/**
 * 会员刷新令牌仓储端口（CHG-0016）：family 旋转/重放撤销的持久化抽象，
 * 行落在 member_refresh_token（与 auth_refresh_token 物理隔离）。
 */
public interface MemberRefreshSessionRepository {

    Optional<MemberRefreshSession> findByDigest(String digest);

    void save(MemberRefreshSession token);

    /** 条件消费（旋转）：仅当 ACTIVE 且 auth_version 匹配时置 used_at，返回是否成功。 */
    boolean consume(String digest, long authVersion, Instant usedAt);

    /** 重放/异常时撤销整个 family（同 family_id 全部置 revoked_at）。 */
    void revokeFamily(String familyId, Instant revokedAt);

    /** 退出登录时撤销该会员全部未撤销令牌。 */
    void revokeAllForMember(long memberId, Instant revokedAt);
}
