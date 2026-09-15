package com.ai.mall.member.infrastructure.persistence.member;

import com.ai.mall.member.domain.exception.DuplicateResourceException;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.domain.repository.MemberProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 会员档案仓储 MyBatis 实现（CHG-0016）。
 *
 * <p>uk_profile_event / 主键冲突统一转译 {@link DuplicateResourceException}，
 * 供 provision 服务做并发重放幂等。
 */
@Repository
public class MyBatisMemberProfileRepository implements MemberProfileRepository {

    private final MemberProfileMapper mapper;

    public MyBatisMemberProfileRepository(MemberProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByInitializedEventId(String eventId) {
        return mapper.countByEventId(eventId) > 0;
    }

    @Override
    public boolean existsByMemberId(long memberId) {
        return mapper.countByMemberId(memberId) > 0;
    }

    @Override
    public MemberProfile add(MemberProfile profile) {
        MemberProfilePo po = new MemberProfilePo();
        po.setMemberId(profile.memberId());
        po.setUsername(profile.username());
        po.setNickname(profile.nickname());
        po.setAvatarUrl(profile.avatarUrl());
        po.setGender(profile.gender().name());
        po.setPhone(profile.phone());
        po.setEmail(profile.email());
        po.setInitializedEventId(profile.initializedEventId());
        po.setCreatedAt(profile.createdAt());
        po.setUpdatedAt(profile.updatedAt());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("member profile already exists", ex);
        }
        return profile;
    }

    @Override
    public Optional<MemberProfile> findByMemberId(long memberId) {
        return Optional.ofNullable(mapper.findByMemberId(memberId)).map(po -> {
            Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
            Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
            return MemberProfile.reconstitute(po.getMemberId(), po.getUsername(), po.getNickname(),
                    po.getAvatarUrl(), po.getGender(), po.getPhone(), po.getEmail(),
                    po.getInitializedEventId(), created, updated);
        });
    }
}
