package com.ai.mall.search.domain.index;

import java.time.Duration;
import java.time.Instant;

/**
 * 同步失败记录聚合（CHG-0021）。
 *
 * <p>退避序列冻结：30s / 1m / 2m / 5m / 10m，5 次失败转 FAILED_DEAD；
 * 同 (productId,eventType) 的 PENDING 行复用（应用层 upsert 语义，不并历史）。
 */
public class SearchSyncFailure {

    /** 第 1..4 次失败后的退避；第 5 次失败即终态。 */
    public static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
            Duration.ofMinutes(5), Duration.ofMinutes(10)
    };
    public static final int MAX_RETRIES = 5;

    private Long id;
    private long productId;
    private SyncEventType eventType;
    private SyncFailureStatus status;
    private int retryCount;
    private int maxRetries = MAX_RETRIES;
    private String lastError;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static SearchSyncFailure register(long productId, SyncEventType eventType,
                                             String lastError, Instant now) {
        SearchSyncFailure failure = new SearchSyncFailure();
        failure.productId = productId;
        failure.eventType = eventType;
        failure.status = SyncFailureStatus.PENDING;
        failure.retryCount = 0;
        failure.maxRetries = MAX_RETRIES;
        failure.lastError = truncate(lastError);
        failure.nextRetryAt = now.plus(BACKOFF[0]);
        failure.createdAt = now;
        failure.updatedAt = now;
        return failure;
    }

    /** 复用既有 PENDING 行：刷新错误信息与首次退避窗口。 */
    public void refresh(String lastError, Instant now) {
        this.status = SyncFailureStatus.PENDING;
        this.retryCount = 0;
        this.lastError = truncate(lastError);
        this.nextRetryAt = now.plus(BACKOFF[0]);
        this.updatedAt = now;
    }

    /** 一次重放失败：退避升档；达到上限转 FAILED_DEAD。 */
    public void recordRetryFailure(String lastError, Instant now) {
        int next = this.retryCount + 1;
        this.retryCount = next;
        this.lastError = truncate(lastError);
        if (next >= maxRetries) {
            this.status = SyncFailureStatus.FAILED_DEAD;
            this.nextRetryAt = now;
        } else {
            this.status = SyncFailureStatus.PENDING;
            this.nextRetryAt = now.plus(BACKOFF[next - 1]);
        }
        this.updatedAt = now;
    }

    public void markSuccess(Instant now) {
        this.status = SyncFailureStatus.SUCCESS;
        this.nextRetryAt = now;
        this.updatedAt = now;
    }

    /** 人工重试：回到 PENDING、清零计数、立即被扫描拾取。 */
    public void rearm(Instant now) {
        this.status = SyncFailureStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.updatedAt = now;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getProductId() { return productId; }
    public void setProductId(long productId) { this.productId = productId; }
    public SyncEventType getEventType() { return eventType; }
    public void setEventType(SyncEventType eventType) { this.eventType = eventType; }
    public SyncFailureStatus getStatus() { return status; }
    public void setStatus(SyncFailureStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
