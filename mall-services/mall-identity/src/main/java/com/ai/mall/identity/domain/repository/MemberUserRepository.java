package com.ai.mall.identity.domain.repository;

import com.ai.mall.identity.domain.model.member.MemberAccount;
import java.util.Optional;

/**
 * 会员账号仓储端口（CHG-0016）：identity 域会员账号持久化抽象。
 *
 * <p>查重以归一化用户名（username_norm）为准；插入命中 uk_member_username_norm
 * 由基础设施转译为 {@code DuplicateResourceException}。
 */
public interface MemberUserRepository {

    /** 归一化用户名是否已存在（大小写不敏感）。 */
    boolean existsByUsernameNorm(String usernameNorm);

    /**
     * 新建会员账号并回填自增 id。
     *
     * @throws com.ai.mall.identity.domain.exception.DuplicateResourceException username_norm 唯一冲突
     */
    MemberAccount add(MemberAccount account);

    /** 按主键加载（profile-seed 内部端点/登录刷新加载账号使用）。 */
    Optional<MemberAccount> findById(long memberId);

    /** 按归一化用户名加载（登录查询，大小写不敏感）。 */
    Optional<MemberAccount> findByUsernameNorm(String usernameNorm);

    /** 退出登录时原子递增认证版本，使版本窗口内的旧 access claim 变为陈旧。 */
    void incrementAuthVersion(long memberId);
}
