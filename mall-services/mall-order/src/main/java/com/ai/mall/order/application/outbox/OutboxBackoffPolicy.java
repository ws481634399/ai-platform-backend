package com.ai.mall.order.application.outbox;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Outbox 投递退避策略：有界指数退避。
 *
 * <p>nextRetryAt = now + min(initialDelay * factor^retryCount, maxDelay)。</p>
 */
@Component
public class OutboxBackoffPolicy {

    private final Duration initialDelay;
    private final double factor;
    private final Duration maxDelay;
    private final int maxRetries;

    public OutboxBackoffPolicy(
            @Value("${outbox.delivery.backoff.initial-delay:5s}") Duration initialDelay,
            @Value("${outbox.delivery.backoff.factor:2.0}") double factor,
            @Value("${outbox.delivery.backoff.max-delay:10m}") Duration maxDelay,
            @Value("${outbox.delivery.backoff.max-retries:10}") int maxRetries) {
        this.initialDelay = initialDelay;
        this.factor = factor;
        this.maxDelay = maxDelay;
        this.maxRetries = maxRetries;
    }

    /** 计算第 retryCount 次失败后的下次可投递时间。 */
    public Instant nextRetryAt(int retryCount) {
        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(factor, retryCount));
        Duration delay = Duration.ofMillis(Math.min(delayMillis, maxDelay.toMillis()));
        return Instant.now().plus(delay);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /** 判断已达最大重试次数（应转 FAILED）。 */
    public boolean shouldFail(int nextRetryCount) {
        return nextRetryCount >= maxRetries;
    }
}
