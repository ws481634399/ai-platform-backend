package com.ai.mall.member.domain.model.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 会员档案修改行为不变量测试（CHG-0016 STORY-003-01-02-01 / TC-002）。
 */
@DisplayName("MemberProfile 资料修改不变量")
class MemberProfileUpdateTest {

    private MemberProfile reconstituted() {
        return MemberProfile.reconstitute(72000001L, "Seed_Member", "会员000001", null,
                "UNKNOWN", null, null, "evt-seed-1",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.parse("2026-09-15T10:00:00Z"));
    }

    @Test
    @DisplayName("TC-002 合法昵称/性别/手机/邮箱更新成功")
    void validUpdateApplied() {
        MemberProfile p = reconstituted();
        p.updateProfile("小明", Gender.MALE, "13800138000", "ming@example.com");

        assertThat(p.nickname()).isEqualTo("小明");
        assertThat(p.gender()).isEqualTo(Gender.MALE);
        assertThat(p.phone()).isEqualTo("13800138000");
        assertThat(p.email()).isEqualTo("ming@example.com");
    }

    @Test
    @DisplayName("TC-002 昵称 1 字与 32 字合法；空白/超长非法")
    void nicknameLengthBoundary() {
        MemberProfile p1 = reconstituted();
        p1.updateProfile("甲", Gender.UNKNOWN, null, null);
        assertThat(p1.nickname()).isEqualTo("甲");

        MemberProfile p2 = reconstituted();
        String thirtyTwo = "字".repeat(32);
        p2.updateProfile(thirtyTwo, Gender.UNKNOWN, null, null);
        assertThat(p2.nickname()).hasSize(32);

        MemberProfile blank = reconstituted();
        assertThatThrownBy(() -> blank.updateProfile("   ", Gender.UNKNOWN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        MemberProfile tooLong = reconstituted();
        assertThatThrownBy(() -> tooLong.updateProfile("字".repeat(33), Gender.UNKNOWN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-002 手机号格式矩阵：合法号段通过；非 1 开头/位数错/字母拒绝")
    void phoneFormatMatrix() {
        assertThat(reconstitutedAndReturnPhone("13900139000")).isEqualTo("13900139000");
        assertThat(reconstitutedAndReturnPhone("15912345678")).isEqualTo("15912345678");

        for (String invalid : new String[]{"12345678901", "1390013900", "139001390001", "1380013800a"}) {
            MemberProfile p = reconstituted();
            assertThatThrownBy(() -> p.updateProfile("会员", Gender.UNKNOWN, invalid, null))
                    .as("phone=%s", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("TC-002 邮箱格式矩阵：常规通过；无 @/无域名/超长拒绝")
    void emailFormatMatrix() {
        assertThat(reconstitutedAndReturnEmail("a.b+1@sub.example.com")).isEqualTo("a.b+1@sub.example.com");

        for (String invalid : new String[]{"plainaddress", "a@", "@b.com", "a@b", "a@b..com"}) {
            MemberProfile p = reconstituted();
            assertThatThrownBy(() -> p.updateProfile("会员", Gender.UNKNOWN, null, invalid))
                    .as("email=%s", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        MemberProfile tooLong = reconstituted();
        // 124 个 a + "@b.cn"(5) = 129 > VARCHAR(128)
        assertThatThrownBy(() -> tooLong.updateProfile("会员", Gender.UNKNOWN, null, "a".repeat(124) + "@b.cn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("手机号/邮箱允许显式置 null（清空在应用层归一后进入）")
    void nullableContactAllowed() {
        MemberProfile p = reconstituted();
        p.updateProfile("会员", Gender.FEMALE, null, null);
        assertThat(p.phone()).isNull();
        assertThat(p.email()).isNull();
        assertThat(p.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("TC-004 changeAvatar 接受合法 URL 并替换；空白拒绝")
    void changeAvatar() {
        MemberProfile p = reconstituted();
        String url = "http://localhost:9000/mall-avatar/member-avatar/72000001/uuid.jpg";
        p.changeAvatar(url);
        assertThat(p.avatarUrl()).isEqualTo(url);

        MemberProfile p2 = reconstituted();
        assertThatThrownBy(() -> p2.changeAvatar("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    private String reconstitutedAndReturnPhone(String phone) {
        MemberProfile p = reconstituted();
        p.updateProfile("会员", Gender.UNKNOWN, phone, null);
        return p.phone();
    }

    private String reconstitutedAndReturnEmail(String email) {
        MemberProfile p = reconstituted();
        p.updateProfile("会员", Gender.UNKNOWN, null, email);
        return p.email();
    }
}
