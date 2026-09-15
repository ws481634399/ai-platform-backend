package com.ai.mall.member.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.mall.member.domain.exception.DuplicateResourceException;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.domain.repository.MemberProfileRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * profile provision 双幂等测试（CHG-0016 / TC-004、TC-005）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("STORY-003-01-01-01 profile provision 双幂等")
class ProfileProvisionServiceTest {

    @Autowired ProfileProvisionService service;
    @Autowired MemberProfileRepository repository;
    @Autowired JdbcTemplate jdbc;

    private ProfileProvisionService.ProvisionCommand command(String eventId, long memberId, String nickname) {
        return new ProfileProvisionService.ProvisionCommand(
                eventId, memberId, "user_" + memberId, nickname, Instant.now());
    }

    @Test
    @DisplayName("TC-004 首次 provision 建行：memberId 一致、事件昵称优先、性别默认 UNKNOWN")
    void firstProvisionCreatesProfileWithEventNickname() {
        boolean provisioned = service.provision(command("evt-001", 51001234L, "早到的小明"));

        assertThat(provisioned).isTrue();
        MemberProfile profile = repository.findByMemberId(51001234L).orElseThrow();
        assertThat(profile.username()).isEqualTo("user_51001234");
        assertThat(profile.nickname()).isEqualTo("早到的小明");
        assertThat(profile.gender().name()).isEqualTo("UNKNOWN");
        assertThat(profile.initializedEventId()).isEqualTo("evt-001");
        assertThat(profile.avatarUrl()).isNull();
    }

    @Test
    @DisplayName("TC-004 事件昵称为空时本地兜底：\"会员\"+memberId 后 6 位")
    void blankNicknameFallsBackToDefault() {
        service.provision(command("evt-002", 7L, "  "));

        assertThat(repository.findByMemberId(7L).orElseThrow().nickname()).isEqualTo("会员7");
    }

    @Test
    @DisplayName("TC-005 同 eventId 重放 → provisioned=false 且仅一行")
    void sameEventReplayIsIdempotent() {
        assertThat(service.provision(command("evt-003", 81004567L, "昵称A"))).isTrue();
        assertThat(service.provision(command("evt-003", 81004567L, "昵称A"))).isFalse();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE member_id = 81004567",
                Integer.class)).isEqualTo(1);
        // 重放不覆盖原昵称
        assertThat(repository.findByMemberId(81004567L).orElseThrow().nickname()).isEqualTo("昵称A");
    }

    @Test
    @DisplayName("不同 eventId 但同 memberId 重复投递 → provisioned=false 且仅一行")
    void sameMemberDifferentEventAlsoIdempotent() {
        assertThat(service.provision(command("evt-004a", 91007890L, "昵称X"))).isTrue();
        assertThat(service.provision(command("evt-004b", 91007890L, "昵称Y"))).isFalse();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE member_id = 91007890",
                Integer.class)).isEqualTo(1);
        assertThat(repository.findByMemberId(91007890L).orElseThrow().initializedEventId()).isEqualTo("evt-004a");
    }

    @Test
    @DisplayName("uk_profile_event 并发兜底：仓储直接抛 DuplicateResourceException（供服务 catch 分支使用）")
    void uniqueConstraintRaisesDuplicateResourceException() {
        service.provision(command("evt-005", 61001111L, "昵称P"));
        MemberProfile duplicate = MemberProfile.provision(61002222L, "user_61002222", "昵称Q",
                "evt-005", Instant.now());
        assertThatThrownBy(() -> repository.add(duplicate))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE member_id = 61002222",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("并发重放竞态：预检查通过但 add 撞唯一键 → 服务返回 false（不抛错）")
    void raceBetweenCheckAndInsertReturnsFalse() {
        MemberProfileRepository racing = mock(MemberProfileRepository.class);
        when(racing.existsByInitializedEventId(any())).thenReturn(false);
        when(racing.existsByMemberId(anyLong())).thenReturn(false);
        when(racing.add(any())).thenThrow(new DuplicateResourceException("uk race", new RuntimeException()));
        ProfileProvisionService racingService = new ProfileProvisionService(racing);

        boolean result = racingService.provision(command("evt-006", 61003333L, "昵称R"));
        assertThat(result).isFalse();
    }
}
