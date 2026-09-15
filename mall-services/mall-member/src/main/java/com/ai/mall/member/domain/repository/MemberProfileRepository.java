package com.ai.mall.member.domain.repository;

import com.ai.mall.member.domain.model.member.MemberProfile;
import java.util.Optional;

/**
 * 会员档案仓储端口（CHG-0016）。
 *
 * <p>双幂等查询键：initialized_event_id（事件重放）与 member_id（补偿重复）；
 * 插入命中 uk_profile_event 由基础设施转译 DuplicateResourceException。
 */
public interface MemberProfileRepository {

    /** 事件 ID 是否已初始化过档案（第一道幂等）。 */
    boolean existsByInitializedEventId(String eventId);

    /** 会员是否已有档案（第二道幂等/懒补偿判定）。 */
    boolean existsByMemberId(long memberId);

    /**
     * 新建档案。
     *
     * @throws com.ai.mall.member.domain.exception.DuplicateResourceException 唯一键并发冲突（第二道兜底）
     */
    MemberProfile add(MemberProfile profile);

    /**
     * 更新可变资料（昵称/性别/手机/邮箱/头像）（STORY-003-01-02-01）。
     * updated_at 由数据库 NOW(6) 维护；仅按主键更新，返回受影响行数。
     */
    int update(MemberProfile profile);

    Optional<MemberProfile> findByMemberId(long memberId);
}
