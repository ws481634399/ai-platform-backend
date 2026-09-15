package com.ai.mall.identity.application.member;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.domain.model.member.MemberRefreshSession;
import com.ai.mall.identity.domain.repository.MemberRefreshSessionRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员刷新令牌会话服务（CHG-0016 / STORY-003-01-01-02）。
 *
 * <p>移植 ADMIN RefreshSession 家族算法，独立类操作 member_refresh_token，不改 admin 代码：
 * 登录新建 family；刷新时 ACTIVE 令牌条件消费（used_at）并在同 family 发新令牌（旋转）；
 * 一旦发现已旋转/已撤销/过期/auth_version 陈旧的令牌被再次提交（重放），撤销整个 family。
 * 原始 refresh token 只出参一次，库内仅存 SHA-256 摘要。
 */
@Service
public class MemberRefreshSessionService {

    private final MemberRefreshSessionRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration lifetime;

    @org.springframework.beans.factory.annotation.Autowired
    public MemberRefreshSessionService(MemberRefreshSessionRepository repository,
                                       @Value("${mall.security.jwt.refresh-ttl:P7D}") Duration lifetime) {
        this(repository, Clock.systemUTC(), new SecureRandom(), lifetime);
    }

    MemberRefreshSessionService(MemberRefreshSessionRepository repository, Clock clock,
                                SecureRandom random, Duration lifetime) {
        this.repository = repository;
        this.clock = clock;
        this.random = random;
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("refresh token lifetime must be positive");
        }
        this.lifetime = lifetime;
    }

    /** 登录：新建 family 并签发首个 refresh token。 */
    @Transactional
    public IssuedRefreshToken issue(long memberId, long authVersion) {
        return issue(memberId, authVersion, UUID.randomUUID().toString());
    }

    /** 刷新：校验 ACTIVE/版本 → 条件消费旧令牌 → 同 family 签发新令牌；异常路径整族撤销。 */
    @Transactional
    public IssuedRefreshToken rotate(String rawToken, long currentAuthVersion) {
        Instant now = clock.instant();
        MemberRefreshSession existing = repository.findByDigest(digest(rawToken))
                .orElseThrow(() -> invalidRefreshToken());
        if (!existing.isActive(now) || existing.authVersion() != currentAuthVersion) {
            repository.revokeFamily(existing.familyId(), now);
            throw invalidRefreshToken();
        }
        if (!repository.consume(existing.digest(), currentAuthVersion, now)) {
            // 并发/重放：条件更新失败说明令牌刚被消费——撤销整族
            repository.revokeFamily(existing.familyId(), now);
            throw invalidRefreshToken();
        }
        return issue(existing.memberId(), existing.authVersion(), existing.familyId());
    }

    /** 仅查找，不修改状态（供 TokenPair 刷新时加载 memberId/authVersion）。 */
    public MemberRefreshSession inspect(String rawToken) {
        return repository.findByDigest(digest(rawToken)).orElseThrow(MemberRefreshSessionService::invalidRefreshToken);
    }

    /** 退出：撤销该会员全部 refresh 令牌。 */
    @Transactional
    public void revokeAll(long memberId) {
        repository.revokeAllForMember(memberId, clock.instant());
    }

    private IssuedRefreshToken issue(long memberId, long authVersion, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(lifetime);
        repository.save(new MemberRefreshSession(digest(raw), familyId, memberId, authVersion, expiresAt, null, null));
        return new IssuedRefreshToken(raw, expiresAt, familyId, memberId, authVersion);
    }

    static String digest(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalidRefreshToken();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static UseCaseException invalidRefreshToken() {
        return new UseCaseException(UseCaseException.Kind.UNAUTHORIZED, "invalid refresh token");
    }

    /** 已签发的 refresh token（明文仅此一次返回，库内只有摘要）。 */
    public record IssuedRefreshToken(String value, Instant expiresAt, String familyId,
                                     long memberId, long authVersion) {
    }
}
