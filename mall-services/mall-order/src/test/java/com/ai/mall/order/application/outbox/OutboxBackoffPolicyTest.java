package com.ai.mall.order.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** OutboxBackoffPolicy 退避策略单测（TC-003/TC-006）。 */
class OutboxBackoffPolicyTest {

    private final OutboxBackoffPolicy policy = new OutboxBackoffPolicy(
            Duration.ofSeconds(5), 2.0, Duration.ofMinutes(10), 3);

    @Test
    @DisplayName("退避时间随重试次数指数增长且不超过 maxDelay")
    void backoffGrowsExponentially() {
        long t1 = policy.nextRetryAt(0).toEpochMilli();
        long t2 = policy.nextRetryAt(1).toEpochMilli();
        long t3 = policy.nextRetryAt(2).toEpochMilli();
        long t10 = policy.nextRetryAt(10).toEpochMilli();

        long now = System.currentTimeMillis();
        assertThat(t1 - now).isBetween(4_000L, 6_000L);       // ~5s
        assertThat(t2 - now).isBetween(9_000L, 11_000L);      // ~10s
        assertThat(t3 - now).isBetween(19_000L, 21_000L);     // ~20s
        assertThat(t10 - now).isLessThanOrEqualTo(Duration.ofMinutes(10).toMillis() + 1_000L); // 封顶
    }

    @Test
    @DisplayName("shouldFail 在达到 maxRetries 时返回 true")
    void shouldFailAtMaxRetries() {
        assertThat(policy.shouldFail(3)).isTrue();
        assertThat(policy.shouldFail(2)).isFalse();
    }

    @Test
    @DisplayName("maxRetries 配置可读取")
    void exposesMaxRetries() {
        assertThat(policy.getMaxRetries()).isEqualTo(3);
    }
}
