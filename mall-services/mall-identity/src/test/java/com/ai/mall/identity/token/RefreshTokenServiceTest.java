package com.ai.mall.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {
    @Test
    void rotatesOnceAndRevokesFamilyOnReplay() {
        var repository = new MemoryRepository();
        var service = new RefreshTokenService(repository,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(), Duration.ofDays(1));
        var first = service.issue(7, 2);
        var second = service.rotate(first.value(), 2);

        assertThat(second.value()).isNotEqualTo(first.value());
        assertThat(repository.tokens.get(RefreshTokenService.digest(first.value())).usedAt()).isNotNull();
        assertThatThrownBy(() -> service.rotate(first.value(), 2))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);
        assertThat(repository.tokens.get(RefreshTokenService.digest(second.value())).revokedAt()).isNotNull();
    }

    private static final class MemoryRepository implements RefreshTokenRepository {
        private final Map<String, RefreshToken> tokens = new HashMap<>();
        @Override public Optional<RefreshToken> findByDigest(String digest) { return Optional.ofNullable(tokens.get(digest)); }
        @Override public void save(RefreshToken token) { tokens.put(token.digest(), token); }
        @Override public synchronized boolean consume(String digest, long authVersion, Instant at) {
            var token = tokens.get(digest);
            if (token == null || token.authVersion() != authVersion || !token.isActive(at)) return false;
            tokens.put(digest, token.markUsed(at)); return true;
        }
        @Override public void revokeFamily(String familyId, Instant at) {
            tokens.replaceAll((key, token) -> token.familyId().equals(familyId) ? token.revoke(at) : token);
        }
        @Override public void revokeAllForAdmin(long adminId, Instant at) {
            tokens.replaceAll((key, token) -> token.adminId() == adminId ? token.revoke(at) : token);
        }
    }
}
