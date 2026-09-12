package com.ai.mall.identity.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.web.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration lifetime;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${mall.security.jwt.refresh-ttl:P7D}") Duration lifetime) {
        this(repository, Clock.systemUTC(), new SecureRandom(), lifetime);
    }

    RefreshTokenService(RefreshTokenRepository repository, Clock clock, SecureRandom random, Duration lifetime) {
        this.repository = repository;
        this.clock = clock;
        this.random = random;
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("refresh token lifetime must be positive");
        this.lifetime = lifetime;
    }

    @Transactional
    public IssuedRefreshToken issue(long adminId, long authVersion) {
        return issue(adminId, authVersion, UUID.randomUUID().toString());
    }

    @Transactional
    public IssuedRefreshToken rotate(String rawToken, long currentAuthVersion) {
        Instant now = clock.instant();
        RefreshToken existing = repository.findByDigest(digest(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("invalid refresh token"));
        if (!existing.isActive(now) || existing.authVersion() != currentAuthVersion) {
            repository.revokeFamily(existing.familyId(), now);
            throw new InvalidRefreshTokenException("refresh token replayed, expired or stale");
        }
        if (!repository.consume(existing.digest(), currentAuthVersion, now)) {
            repository.revokeFamily(existing.familyId(), now);
            throw new InvalidRefreshTokenException("refresh token replayed");
        }
        return issue(existing.adminId(), existing.authVersion(), existing.familyId());
    }

    public RefreshToken inspect(String rawToken) {
        return repository.findByDigest(digest(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("invalid refresh token"));
    }

    @Transactional
    public void revokeAll(long adminId) {
        repository.revokeAllForAdmin(adminId, clock.instant());
    }

    private IssuedRefreshToken issue(long adminId, long authVersion, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(lifetime);
        repository.save(new RefreshToken(digest(raw), familyId, adminId, authVersion, expiresAt, null, null));
        return new IssuedRefreshToken(raw, expiresAt, familyId, adminId, authVersion);
    }

    static String digest(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidRefreshTokenException("invalid refresh token");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record IssuedRefreshToken(String value, Instant expiresAt, String familyId, long adminId, long authVersion) {
    }

    public static final class InvalidRefreshTokenException extends BusinessException {
        public InvalidRefreshTokenException(String message) {
            super(CommonErrorCode.BUSINESS_ERROR, HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
    }
}
