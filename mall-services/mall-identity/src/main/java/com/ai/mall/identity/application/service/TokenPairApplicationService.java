package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.service.AdminAuthenticationApplicationService.AuthenticatedAdmin;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import com.ai.mall.identity.application.port.AccessTokenIssuer;
import com.ai.mall.identity.application.service.RefreshSessionApplicationService;
import org.springframework.stereotype.Service;

@Service
@org.springframework.context.annotation.Profile("!test")
public class TokenPairApplicationService {
    private final AccessTokenIssuer accessTokens;
    private final RefreshSessionApplicationService refreshTokens;
    private final AdminUserRepository admins;
    public TokenPairApplicationService(AccessTokenIssuer accessTokens, RefreshSessionApplicationService refreshTokens, AdminUserRepository admins) {
        this.accessTokens = accessTokens; this.refreshTokens = refreshTokens; this.admins = admins;
    }
    public TokenPair issue(AuthenticatedAdmin admin) {
        var access = accessTokens.issue(admin.id(), admin.username(), admin.authVersion());
        var refresh = refreshTokens.issue(admin.id(), admin.authVersion());
        return new TokenPair(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }
    public TokenPair refresh(String rawRefreshToken) {
        var current = refreshTokens.inspect(rawRefreshToken);
        var admin = admins.findById(current.adminId()).orElseThrow(() -> invalid("admin disabled or missing"));
        try { admin.ensureCanSignIn(); } catch (DomainRuleViolation ex) { throw invalid("admin disabled or missing"); }
        var refresh = refreshTokens.rotate(rawRefreshToken, admin.authVersion());
        var access = accessTokens.issue(refresh.adminId(), admin.account(), refresh.authVersion());
        return new TokenPair(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }
    public void revokeAll(long adminId) { refreshTokens.revokeAll(adminId); }
    private static RefreshSessionApplicationService.InvalidRefreshTokenException invalid(String message) {
        return new RefreshSessionApplicationService.InvalidRefreshTokenException(message);
    }
    public record TokenPair(String accessToken, java.time.Instant accessExpiresAt, String refreshToken, java.time.Instant refreshExpiresAt) {}
}
