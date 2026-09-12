package com.ai.mall.identity.token;

import com.ai.mall.identity.auth.AdminAuthenticationService.AuthenticatedAdmin;
import com.ai.mall.identity.auth.AdminCredentialRepository;

public class TokenPairService {
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final AdminCredentialRepository admins;

    public TokenPairService(AccessTokenService accessTokens, RefreshTokenService refreshTokens,
                            AdminCredentialRepository admins) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.admins = admins;
    }

    public TokenPair issue(AuthenticatedAdmin admin) {
        var access = accessTokens.issue(admin.id(), admin.username(), admin.authVersion());
        var refresh = refreshTokens.issue(admin.id(), admin.authVersion());
        return new TokenPair(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }

    public TokenPair refresh(String rawRefreshToken) {
        var current = refreshTokens.inspect(rawRefreshToken);
        var admin = admins.findById(current.adminId())
                .filter(com.ai.mall.identity.auth.AdminCredential::canAuthenticate)
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException("admin disabled or missing"));
        var refresh = refreshTokens.rotate(rawRefreshToken, admin.authVersion());
        var access = accessTokens.issue(refresh.adminId(), admin.username(), refresh.authVersion());
        return new TokenPair(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }

    public void revokeAll(long adminId) { refreshTokens.revokeAll(adminId); }

    public record TokenPair(String accessToken, java.time.Instant accessExpiresAt,
                            String refreshToken, java.time.Instant refreshExpiresAt) {}
}
