package com.ai.mall.identity.domain.model.member;

import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import java.time.Instant;

/**
 * 会员账号聚合根（CHG-0016）。
 *
 * <p>承载会员身份侧全部写状态：用户名（原始+归一）、BCrypt 密码哈希、状态、认证版本。
 * 注册工厂仅负责聚合内规则（用户名/密码策略在应用服务调用 {@link MemberPasswordPolicy} 后传入哈希），
 * 不接触任何框架；密码哈希由应用层经 PasswordHasher 端口完成，聚合内只见哈希、不见明文。
 */
public final class MemberAccount {

    private final long id;
    private final MemberUsername username;
    private final String passwordHash;
    private MemberStatus status;
    private long authVersion;
    private final Instant createdAt;
    private Instant updatedAt;

    private MemberAccount(long id, MemberUsername username, String passwordHash, MemberStatus status,
                          long authVersion, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
        this.authVersion = authVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 注册新会员：id 暂为 0（由持久化自增回填），状态 ENABLED、authVersion=1。
     *
     * @param username          已通过规则校验的用户名
     * @param encodedPasswordHash 已 BCrypt 哈希的密码（禁止传入明文）
     */
    public static MemberAccount register(MemberUsername username, String encodedPasswordHash, Instant now) {
        if (encodedPasswordHash == null || encodedPasswordHash.isBlank() || encodedPasswordHash.length() > 255) {
            throw new IllegalArgumentException("password hash is required");
        }
        return new MemberAccount(0, username, encodedPasswordHash, MemberStatus.ENABLED, 1, now, now);
    }

    /** 持久化重建：自增 id 回填与状态还原，不重复执行业务规则。 */
    public static MemberAccount reconstitute(long id, String username, String passwordHash, String status,
                                             long authVersion, Instant createdAt, Instant updatedAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("member id must be positive");
        }
        return new MemberAccount(id, MemberUsername.of(username), passwordHash,
                MemberStatus.valueOf(status), authVersion, createdAt, updatedAt);
    }

    /** 登录前置判定：仅 ENABLED 可签发会话。 */
    public void ensureCanSignIn() {
        if (status != MemberStatus.ENABLED) {
            throw new DomainRuleViolation("member cannot sign in");
        }
    }

    public long id() {
        return id;
    }

    public MemberUsername username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public MemberStatus status() {
        return status;
    }

    public long authVersion() {
        return authVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
