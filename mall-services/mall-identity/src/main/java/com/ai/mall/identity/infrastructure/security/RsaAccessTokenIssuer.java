package com.ai.mall.identity.infrastructure.security;

import com.ai.mall.identity.application.port.AccessTokenIssuer;
import com.ai.mall.common.security.SubjectType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class RsaAccessTokenIssuer implements AccessTokenIssuer {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;

    public RsaAccessTokenIssuer(JwtEncoder encoder, Clock clock, String issuer, String audience, Duration lifetime) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("access token lifetime must be between 1ns and 24h");
        this.lifetime = lifetime;
    }

    @Override public IssuedAccessToken issue(long adminId, String username, long authVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(java.util.List.of(audience)).subject(Long.toString(adminId))
                .issuedAt(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("subject_type", SubjectType.ADMIN.name())
                .claim("username", username).claim("auth_version", authVersion).build();
        return new IssuedAccessToken(encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue(), expiresAt);
    }

}
