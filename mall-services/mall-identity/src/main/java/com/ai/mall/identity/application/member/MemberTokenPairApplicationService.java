package com.ai.mall.identity.application.member;

import com.ai.mall.common.security.SubjectType;
import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.member.MemberAuthenticationApplicationService.AuthenticatedMember;
import com.ai.mall.identity.application.port.AccessTokenIssuer;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员双令牌应用服务（CHG-0016 / STORY-003-01-01-02）。
 *
 * <p>登录签发 access+refresh；刷新时先查账号当前状态与 auth_version（禁用/不存在 → 401），
 * 旋转 refresh 后按新版本重签 access；退出在同一事务内撤销全部 refresh 并 auth_version+1，
 * 使版本窗口内的旧 access claim 即刻陈旧。
 *
 * <p>不带 {@code @Profile("!test")}：会员侧新惯例（CHG-0016），测试走真实安全链，
 * AccessTokenIssuer 由测试配置以进程内 RSA 提供。
 */
@Service
public class MemberTokenPairApplicationService {

    private final AccessTokenIssuer accessTokens;
    private final MemberRefreshSessionService refreshTokens;
    private final MemberUserRepository members;

    public MemberTokenPairApplicationService(AccessTokenIssuer accessTokens,
                                             MemberRefreshSessionService refreshTokens,
                                             MemberUserRepository members) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.members = members;
    }

    /** 登录成功后签发双令牌。 */
    public MemberTokenPair issue(AuthenticatedMember member) {
        var access = accessTokens.issue(member.memberId(), member.username(),
                member.authVersion(), SubjectType.MEMBER);
        var refresh = refreshTokens.issue(member.memberId(), member.authVersion());
        return new MemberTokenPair(access.value(), access.expiresAt(),
                refresh.value(), refresh.expiresAt(), member.memberId());
    }

    /** 刷新：账号存在且可登录（当前 auth_version）→ 旋转 refresh → 重签 access。 */
    public MemberTokenPair refresh(String rawRefreshToken) {
        var current = refreshTokens.inspect(rawRefreshToken);
        MemberAccount member = members.findById(current.memberId())
                .orElseThrow(MemberTokenPairApplicationService::invalid);
        try {
            member.ensureCanSignIn();
        } catch (DomainRuleViolation ex) {
            throw invalid();
        }
        var refresh = refreshTokens.rotate(rawRefreshToken, member.authVersion());
        var access = accessTokens.issue(refresh.memberId(), member.username().value(),
                refresh.authVersion(), SubjectType.MEMBER);
        return new MemberTokenPair(access.value(), access.expiresAt(),
                refresh.value(), refresh.expiresAt(), member.id());
    }

    /** 退出：撤销该会员全部 refresh 并原子递增 auth_version（同事务）。 */
    @Transactional
    public void signOut(long memberId) {
        refreshTokens.revokeAll(memberId);
        members.incrementAuthVersion(memberId);
    }

    private static UseCaseException invalid() {
        return new UseCaseException(UseCaseException.Kind.UNAUTHORIZED, "invalid refresh token");
    }

    /** 会员双令牌响应（refreshToken 在响应体返回给商城前端，同时控制器双发 HttpOnly cookie）。 */
    public record MemberTokenPair(String accessToken, java.time.Instant accessExpiresAt,
                                  String refreshToken, java.time.Instant refreshExpiresAt,
                                  long memberId) {
    }
}
