package com.ai.mall.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.identity.domain.model.session.RefreshSession;
import com.ai.mall.identity.domain.repository.RefreshSessionRepository;
import com.ai.mall.identity.infrastructure.security.BCryptPasswordHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * M1 测试设计 CHG-0007 补充证据：
 * BE-101 (密码哈希/敏感信息)、BE-103 (refresh 摘要存储)、BE-104 (过期/并发旋转) 的自动化覆盖。
 */
class M1SecurityScenariosTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Test
    @DisplayName("BE-101/TC-001 密码经真实 BCrypt 不可逆哈希存储且可校验")
    void bcryptHashIsIrreversibleAndVerifiable() {
        var encoder = new BCryptPasswordEncoder();
        var hasher = new BCryptPasswordHasher(encoder);
        String raw = "Admin@123456";

        String hash = hasher.hash(raw);

        assertThat(hash).startsWith("$2").hasSize(60);
        assertThat(hash).isNotEqualTo(raw).doesNotContain(raw);
        assertThat(hasher.matches(raw, hash)).isTrue();
        assertThat(hasher.matches(raw + "junk", hash)).isFalse();
        String second = hasher.hash(raw);
        assertThat(second).isNotEqualTo(hash).startsWith("$2");
        assertThat(hasher.matches(raw, second)).isTrue();
    }

    @Test
    @DisplayName("BE-101/TC-003 登录/管理/会话响应记录不含密码与敏感令牌字段")
    void authAndAdminViewsExposeNoSensitiveFields() {
        assertThat(AdminUserApplicationService.AdminView.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("password", "passwordHash", "secret", "token");
        assertThat(AdminAuthenticationApplicationService.AuthenticatedAdmin.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("password", "passwordHash", "secret", "token");
        assertThat(AdminSessionQueryService.User.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("password", "passwordHash", "secret", "token");
        assertThat(AdminSessionQueryService.Bootstrap.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("password", "passwordHash", "secret", "token");
    }

    @Test
    @DisplayName("BE-103/TC-003 refresh 仅存 64 位 SHA-256 摘要，明文令牌不落入仓库")
    void refreshTokenPersistsDigestOnly() {
        var repository = new MemoryRepository();
        var service = new RefreshSessionApplicationService(repository,
                Clock.fixed(T0, UTC), new SecureRandom(), Duration.ofDays(7));

        var issued = service.issue(7, 3);

        String digest = RefreshSessionApplicationService.digest(issued.value());
        assertThat(digest).matches("^[0-9a-f]{64}$");
        RefreshSession stored = repository.tokens.get(digest);
        assertThat(stored).isNotNull();
        assertThat(stored.digest()).isEqualTo(digest);
        assertThat(stored.digest()).isNotEqualTo(issued.value());
        assertThat(stored.adminId()).isEqualTo(7);
        assertThat(stored.authVersion()).isEqualTo(3);
        assertThat(stored.expiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
        assertThat(stored.usedAt()).isNull();
        assertThat(stored.revokedAt()).isNull();
        assertThat(repository.tokens.keySet()).doesNotContain(issued.value());
        assertThat(repository.tokens.values())
                .extracting(RefreshSession::digest)
                .allMatch(token -> !token.equals(issued.value()));
    }

    @Test
    @DisplayName("BE-104/TC-002 过期 refresh 令牌被拒绝并撤销族")
    void expiredRefreshTokenIsRejectedAndFamilyRevoked() {
        var repository = new MemoryRepository();
        var service = new RefreshSessionApplicationService(repository,
                Clock.fixed(T0, UTC), new SecureRandom(), Duration.ofHours(1));
        var issued = service.issue(7, 2);

        var later = new RefreshSessionApplicationService(repository,
                Clock.fixed(T0.plusSeconds(3601), UTC), new SecureRandom(), Duration.ofHours(1));

        assertThatThrownBy(() -> later.rotate(issued.value(), 2))
                .isInstanceOf(RefreshSessionApplicationService.InvalidRefreshTokenException.class);
        assertThat(repository.tokens.get(RefreshSessionApplicationService.digest(issued.value())).revokedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("BE-104/TC-003 并发旋转同一 refresh 令牌仅允许一次成功")
    void concurrentRotationSucceedsOnlyOnce() throws Exception {
        var repository = new MemoryRepository();
        var service = new RefreshSessionApplicationService(repository,
                Clock.fixed(T0, UTC), new SecureRandom(), Duration.ofDays(1));
        var issued = service.issue(7, 2);

        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.rotate(issued.value(), 2);
                    return true;
                } catch (RefreshSessionApplicationService.InvalidRefreshTokenException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        long successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) successes++;
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThatThrownBy(() -> service.rotate(issued.value(), 2))
                .isInstanceOf(RefreshSessionApplicationService.InvalidRefreshTokenException.class);
    }

    private static final class MemoryRepository implements RefreshSessionRepository {
        private final Map<String, RefreshSession> tokens = new ConcurrentHashMap<>();

        @Override public Optional<RefreshSession> findByDigest(String digest) { return Optional.ofNullable(tokens.get(digest)); }
        @Override public void save(RefreshSession token) { tokens.put(token.digest(), token); }
        @Override public synchronized boolean consume(String digest, long authVersion, Instant at) {
            var token = tokens.get(digest);
            if (token == null || token.authVersion() != authVersion || !token.isActive(at)) return false;
            tokens.put(digest, token.markUsed(at));
            return true;
        }
        @Override public synchronized void revokeFamily(String familyId, Instant at) {
            tokens.replaceAll((key, token) -> token.familyId().equals(familyId) ? token.revoke(at) : token);
        }
        @Override public synchronized void revokeAllForAdmin(long adminId, Instant at) {
            tokens.replaceAll((key, token) -> token.adminId() == adminId ? token.revoke(at) : token);
        }
    }
}