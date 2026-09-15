package com.ai.mall.identity.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.ai.mall.identity.application.port.MemberProvisioner;
import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;
import com.ai.mall.identity.domain.repository.MemberEventOutboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Outbox relay 故障与原子性测试（CHG-0016 / TC-006、TC-007）。
 *
 * <p>不发真实 HTTP：MemberProvisioner 以 mock 注入；@Scheduled 方法直接手动调用。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("STORY-003-01-01-01 Outbox-Lite relay 重试/上限/回滚")
class MemberProvisionRelayTest {

    @Autowired MemberRegistrationService registration;
    @Autowired MemberProvisionRelay relay;
    @Autowired MemberEventOutboxRepository outbox;
    @MockitoBean MemberProvisioner provisioner;
    @MockitoSpyBean MemberEventOutboxRepository outboxSpy;

    @Test
    @DisplayName("TC-006a 对端首次不可用：afterCommit 失败保 PENDING(retry=1) → 定时重试成功 DONE")
    void pendingRetriedUntilDone() {
        // 第一次投递抛错（afterCommit），之后成功（定时扫描）
        doThrow(new RuntimeException("member service down"))
                .doNothing()
                .when(provisioner).provision(any());

        long memberId = registration.register("Retry_User", "Abcd1234");

        String eventId = outbox.findPending(10).stream()
                .filter(p -> p.event().memberId() == memberId)
                .findFirst().orElseThrow().eventId();
        assertThat(outbox.findPendingByEventId(eventId)).isPresent();
        assertThat(outbox.findPendingByEventId(eventId).orElseThrow().retryCount()).isOne();

        // 模拟 30s 定时扫描
        relay.dispatchPending();

        assertThat(outbox.findPendingByEventId(eventId)).isEmpty();
    }

    @Test
    @DisplayName("TC-006b 重试达 20 次上限：保 PENDING、计数封顶，后续扫描跳过")
    void retryCapKeepsPendingAndStopsRetrying() {
        MemberRegisteredEvent event = MemberRegisteredEvent.create(88_001L, "dead_letter_user",
                "会员880001", Instant.now());
        outbox.append(event);

        doThrow(new RuntimeException("member permanently down")).when(provisioner).provision(any());

        for (int i = 0; i < MemberProvisionRelay.MAX_RETRIES + 1; i++) {
            relay.dispatchPending();
        }

        var stuck = outbox.findPending(Integer.MAX_VALUE).stream()
                .filter(p -> p.eventId() == event.eventId()).findFirst().orElseThrow();
        assertThat(stuck.retryCount()).isEqualTo(MemberProvisionRelay.MAX_RETRIES);
        // 再扫描一轮：已达上限不再投递、计数不再增长
        relay.dispatchPending();
        var stillStuck = outbox.findPending(Integer.MAX_VALUE).stream()
                .filter(p -> p.eventId() == event.eventId()).findFirst().orElseThrow();
        assertThat(stillStuck.retryCount()).isEqualTo(MemberProvisionRelay.MAX_RETRIES);
    }

    @Test
    @DisplayName("TC-007 outbox 插入失败致注册事务回滚：无 member_user 残留，同名可再注册")
    void outboxFailureRollsBackAccountAndNameIsReusable() {
        doThrow(new RuntimeException("simulated outbox insert failure")).when(outboxSpy).append(any());

        assertThatThrownBy(() -> registration.register("Atomic_1", "Abcd1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated outbox insert failure");

        Long accountRows = jdbcCount("SELECT COUNT(*) FROM member_user WHERE username_norm = 'atomic_1'");
        // 只断言本会员无 outbox 残留（共享上下文内其他用例的事件不参与计数）
        Long outboxRows = jdbcCount("SELECT COUNT(*) FROM member_event_outbox o "
                + "JOIN member_user u ON o.member_id = u.id WHERE u.username_norm = 'atomic_1'");
        assertThat(accountRows).isZero();
        assertThat(outboxRows).isZero();

        // 恢复 outbox 后同名可正常注册（afterCommit 投递成功）
        reset(outboxSpy);
        long memberId = registration.register("Atomic_1", "Abcd1234");
        assertThat(memberId).isPositive();
        assertThat(jdbcCount("SELECT COUNT(*) FROM member_user WHERE username_norm = 'atomic_1'")).isOne();
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Long jdbcCount(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
