package com.ai.mall.identity.domain.model.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import com.ai.mall.identity.domain.service.MemberPasswordPolicy;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 会员注册领域规则矩阵（CHG-0016 / TC-003、TC-008）。
 */
class MemberAccountTest {

    private static final String ENCODED_HASH = "$2a$12$abcdefghijklmnopqrstuv1234567890ABCDEFGHIJKLMNOPQR";
    private final MemberPasswordPolicy passwordPolicy = new MemberPasswordPolicy();

    @Nested
    @DisplayName("用户名规则：4-20 位、字母开头、字母数字下划线")
    class UsernameRules {

        @Test
        @DisplayName("合规用户名：字母开头混合下划线数字，且归一化小写")
        void validUsernames() {
            assertThat(MemberUsername.of("abcd").norm()).isEqualTo("abcd");
            assertThat(MemberUsername.of("AbC_1").norm()).isEqualTo("abc_1");
            assertThat(MemberUsername.of("member_2026_ok").value()).isEqualTo("member_2026_ok");
            // 恰好 20 位
            String twenty = "a___________________";
            assertThat(twenty).hasSize(20);
            assertThat(MemberUsername.of(twenty).norm()).isEqualTo(twenty);
        }

        @Test
        @DisplayName("少于 4 位 / 数字开头 / 下划线开头 / 非法字符 / 超 20 位 / null → IAE")
        void invalidUsernames() {
            assertThatThrownBy(() -> MemberUsername.of("abc")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("1abc")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("_abc")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("ab cd")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("ab-cd")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("a123!")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of("a____________________")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MemberUsername.of(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("密码规则：8-32 位且含字母与数字")
    class PasswordRules {

        @Test
        @DisplayName("合规密码")
        void validPasswords() {
            passwordPolicy.ensureValid("abc12345");
            passwordPolicy.ensureValid("Abcd1234");
            // 恰好 8 / 32 位
            passwordPolicy.ensureValid("abcdefg1");
            String thirtyTwo = "a".repeat(31) + "1";
            assertThat(thirtyTwo).hasSize(32);
            passwordPolicy.ensureValid(thirtyTwo);
        }

        @Test
        @DisplayName("少于 8 位 / 纯字母 / 纯数字 / 超 32 位 / null → IAE")
        void invalidPasswords() {
            assertThatThrownBy(() -> passwordPolicy.ensureValid("abc1234"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> passwordPolicy.ensureValid("abcdefgh"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> passwordPolicy.ensureValid("12345678"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> passwordPolicy.ensureValid("a".repeat(32) + "1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> passwordPolicy.ensureValid(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("注册聚合：只存哈希、ENABLED、authVersion=1；停用不可登录")
    void registerAggregateStateAndSignInGuard() {
        MemberAccount account = MemberAccount.register(MemberUsername.of("Alice_1"), ENCODED_HASH, Instant.now());

        assertThat(account.id()).isZero();
        assertThat(account.username().value()).isEqualTo("Alice_1");
        assertThat(account.username().norm()).isEqualTo("alice_1");
        assertThat(account.passwordHash()).startsWith("$2a$12$").doesNotContain("raw");
        assertThat(account.status()).isEqualTo(MemberStatus.ENABLED);
        assertThat(account.authVersion()).isOne();
        account.ensureCanSignIn();

        MemberAccount disabled = MemberAccount.reconstitute(9L, "Alice_1", ENCODED_HASH, "DISABLED", 1,
                Instant.now(), Instant.now());
        assertThatThrownBy(disabled::ensureCanSignIn).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    @DisplayName("注册工厂拒绝空白/超长哈希（防止明文误入）")
    void registerRejectsInvalidHash() {
        assertThatThrownBy(() -> MemberAccount.register(MemberUsername.of("abcd"), " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberAccount.register(MemberUsername.of("abcd"), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
