package com.ai.mall.identity.application.member;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.model.member.MemberUsername;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import org.springframework.stereotype.Service;

/**
 * 会员登录认证应用服务（CHG-0016 / STORY-003-01-01-02）。
 *
 * <p>安全语义：用户不存在与密码错误统一抛出「用户名或密码错误」（401），不可区分账号存在性；
 * 仅 DISABLED 账号在密码验证通过后单独给出「账号已禁用」（403）。
 */
@Service
public class MemberAuthenticationApplicationService {

    /** 凭据错误统一文案（不存在/错密码/格式非法）。 */
    static final String INVALID_CREDENTIALS_MESSAGE = "用户名或密码错误";
    /** 禁用账号文案。 */
    static final String ACCOUNT_DISABLED_MESSAGE = "账号已禁用";

    private final MemberUserRepository members;
    private final PasswordHasher passwords;

    public MemberAuthenticationApplicationService(MemberUserRepository members, PasswordHasher passwords) {
        this.members = members;
        this.passwords = passwords;
    }

    /**
     * 会员登录认证。
     *
     * @throws UseCaseException UNAUTHORIZED 凭据无效（统一文案）；FORBIDDEN 账号已禁用
     */
    public AuthenticatedMember signIn(String rawUsername, String rawPassword) {
        if (rawUsername == null || rawPassword == null) {
            throw invalidCredentials();
        }
        final String usernameNorm;
        try {
            usernameNorm = MemberUsername.of(rawUsername.trim()).norm();
        } catch (IllegalArgumentException ex) {
            throw invalidCredentials();
        }
        if (rawPassword.length() < 8 || rawPassword.length() > 128) {
            throw invalidCredentials();
        }

        MemberAccount member = members.findByUsernameNorm(usernameNorm)
                .orElseThrow(MemberAuthenticationApplicationService::invalidCredentials);
        if (!passwords.matches(rawPassword, member.passwordHash())) {
            throw invalidCredentials();
        }
        try {
            member.ensureCanSignIn();
        } catch (DomainRuleViolation ex) {
            throw new UseCaseException(UseCaseException.Kind.FORBIDDEN, ACCOUNT_DISABLED_MESSAGE);
        }
        return new AuthenticatedMember(member.id(), member.username().value(), member.authVersion());
    }

    private static UseCaseException invalidCredentials() {
        return new UseCaseException(UseCaseException.Kind.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
    }

    /** 认证成功后的会员主体快照（供令牌签发）。 */
    public record AuthenticatedMember(long memberId, String username, long authVersion) {
    }
}
