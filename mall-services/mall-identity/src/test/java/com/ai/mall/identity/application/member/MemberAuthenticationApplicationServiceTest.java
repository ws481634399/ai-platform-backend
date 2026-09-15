package com.ai.mall.identity.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.member.MemberAuthenticationApplicationService.AuthenticatedMember;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.model.member.MemberUsername;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import com.ai.mall.identity.infrastructure.security.BCryptPasswordHasher;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 会员登录认证应用服务单元测试（CHG-0016 / STORY-003-01-01-02 / TC-001~TC-003）。
 *
 * <p>关键安全语义：用户不存在/密码错误/格式非法统一 401 文案，不可区分账号存在性；
 * 仅 DISABLED 在密码通过后给 403「账号已禁用」。
 */
@DisplayName("会员登录认证服务")
class MemberAuthenticationApplicationServiceTest {

    private static final String PASSWORD = "Abcd1234";
    private FakeMemberUserRepository members;
    private MemberAuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        members = new FakeMemberUserRepository();
        service = new MemberAuthenticationApplicationService(
                members, new BCryptPasswordHasher(new BCryptPasswordEncoder(12)));
        members.put(MemberAccount.reconstitute(1001L, "Alice_1",
                new BCryptPasswordEncoder(12).encode(PASSWORD), "ENABLED", 1,
                Instant.now(), Instant.now()));
        members.put(MemberAccount.reconstitute(1002L, "Frozen_1",
                new BCryptPasswordEncoder(12).encode(PASSWORD), "DISABLED", 3,
                Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("TC-001 正确凭据（大小写不敏感）→ 返回会员主体与当前 authVersion")
    void validCredentialsReturnAuthenticatedMember() {
        AuthenticatedMember member = service.signIn("  alICE_1 ", PASSWORD);
        assertThat(member.memberId()).isEqualTo(1001L);
        assertThat(member.username()).isEqualTo("Alice_1");
        assertThat(member.authVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("TC-002 用户不存在 → 401 统一文案")
    void unknownUserGivesUnified401() {
        assertThatThrownBy(() -> service.signIn("nobody_1", PASSWORD))
                .isInstanceOf(UseCaseException.class)
                .satisfies(ex -> {
                    UseCaseException uc = (UseCaseException) ex;
                    assertThat(uc.kind()).isEqualTo(UseCaseException.Kind.UNAUTHORIZED);
                    assertThat(uc.getMessage()).isEqualTo("用户名或密码错误");
                });
    }

    @Test
    @DisplayName("TC-002 密码错误 → 401 同文案（与不存在不可区分）")
    void wrongPasswordGivesUnified401() {
        assertThatThrownBy(() -> service.signIn("alice_1", "WrongPass99"))
                .isInstanceOf(UseCaseException.class)
                .hasMessage("用户名或密码错误")
                .satisfies(ex -> assertThat(((UseCaseException) ex).kind())
                        .isEqualTo(UseCaseException.Kind.UNAUTHORIZED));
    }

    @Test
    @DisplayName("用户名格式非法/密码长度越界 → 401 同文案（不暴露规则差异）")
    void malformedInputGivesUnified401() {
        assertThatThrownBy(() -> service.signIn("abc", PASSWORD))
                .isInstanceOf(UseCaseException.class).hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> service.signIn("1abcd", PASSWORD))
                .isInstanceOf(UseCaseException.class).hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> service.signIn("alice_1", "Ab123"))
                .isInstanceOf(UseCaseException.class).hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> service.signIn(null, PASSWORD))
                .isInstanceOf(UseCaseException.class).hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("TC-003 DISABLED 且密码正确 → 403「账号已禁用」，与凭据错误区分")
    void disabledAccountGives403() {
        assertThatThrownBy(() -> service.signIn("frozen_1", PASSWORD))
                .isInstanceOf(UseCaseException.class)
                .hasMessage("账号已禁用")
                .satisfies(ex -> assertThat(((UseCaseException) ex).kind())
                        .isEqualTo(UseCaseException.Kind.FORBIDDEN));
    }

    /** 简易内存仓储，仅实现本用例所需行为。 */
    static final class FakeMemberUserRepository implements MemberUserRepository {
        private final Map<String, MemberAccount> byNorm = new HashMap<>();

        void put(MemberAccount account) {
            byNorm.put(account.username().norm(), account);
        }

        @Override
        public boolean existsByUsernameNorm(String usernameNorm) {
            return byNorm.containsKey(usernameNorm);
        }

        @Override
        public MemberAccount add(MemberAccount account) {
            byNorm.put(account.username().norm(), account);
            return account;
        }

        @Override
        public Optional<MemberAccount> findById(long memberId) {
            return byNorm.values().stream().filter(a -> a.id() == memberId).findFirst();
        }

        @Override
        public Optional<MemberAccount> findByUsernameNorm(String usernameNorm) {
            return Optional.ofNullable(byNorm.get(usernameNorm));
        }

        @Override
        public void incrementAuthVersion(long memberId) {
            findById(memberId).ifPresent(a -> byNorm.put(a.username().norm(),
                    MemberAccount.reconstitute(a.id(), a.username().value(), a.passwordHash(),
                            a.status().name(), a.authVersion() + 1, a.createdAt(), a.updatedAt())));
        }
    }
}
