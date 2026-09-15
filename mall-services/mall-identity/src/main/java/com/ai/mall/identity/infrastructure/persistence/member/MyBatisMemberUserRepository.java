package com.ai.mall.identity.infrastructure.persistence.member;

import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 会员账号仓储 MyBatis 实现（CHG-0016）。
 *
 * <p>uk_member_username_norm 冲突统一转译 {@link DuplicateResourceException}，
 * 由应用服务映射 409「用户名已存在」。
 */
@Repository
public class MyBatisMemberUserRepository implements MemberUserRepository {

    private final MemberUserMapper mapper;

    public MyBatisMemberUserRepository(MemberUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByUsernameNorm(String usernameNorm) {
        return mapper.countByUsernameNorm(usernameNorm) > 0;
    }

    @Override
    public MemberAccount add(MemberAccount account) {
        MemberUserPo po = new MemberUserPo();
        po.setUsername(account.username().value());
        po.setUsernameNorm(account.username().norm());
        po.setPasswordHash(account.passwordHash());
        po.setStatus(account.status().name());
        po.setAuthVersion(account.authVersion());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("member username already exists", ex);
        }
        Instant created = account.createdAt();
        return MemberAccount.reconstitute(po.getId(), account.username().value(), account.passwordHash(),
                account.status().name(), account.authVersion(), created, created);
    }

    @Override
    public Optional<MemberAccount> findById(long memberId) {
        return Optional.ofNullable(mapper.findById(memberId)).map(po -> {
            Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
            Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
            return MemberAccount.reconstitute(po.getId(), po.getUsername(), po.getPasswordHash(),
                    po.getStatus(), po.getAuthVersion(), created, updated);
        });
    }
}
