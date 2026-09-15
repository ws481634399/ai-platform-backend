package com.ai.mall.identity.domain.model.member;

/**
 * 会员账号状态（CHG-0016）。
 *
 * <p>与 {@code AdminUserStatus} 物理隔离：会员禁用不触碰 ADMIN 体系。
 */
public enum MemberStatus {
    /** 可正常登录。 */
    ENABLED,
    /** 被停用，登录被拒。 */
    DISABLED
}
