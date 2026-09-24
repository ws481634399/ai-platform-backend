package com.ai.mall.order.domain.compensation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 补偿任务聚合（CHG-0019 REQ-M4-004）。
 *
 * <p>同一 {@code (businessType, businessId, operation)} 只有一条任务（登记幂等，重试复用）。
 * 有界退避：30s → 1m → 2m → 5m → 10m，共 5 次；达上限置 FAILED_DEAD，
 * 管理端可在补偿台查看并手动重试。
 */
public class CompensationTask {

    /** 库存释放操作。 */
    public static final String OP_RELEASE_INVENTORY = "RELEASE_INVENTORY";
    /** 库存确认扣减操作。 */
    public static final String OP_CONFIRM_INVENTORY = "CONFIRM_INVENTORY";
    /** 订单自动取消操作（CHG-0025 STORY-009-05-01）。 */
    public static final String OP_AUTO_CANCEL_ORDER = "AUTO_CANCEL_ORDER";
    /** 订单业务类型。 */
    public static final String TYPE_ORDER = "ORDER";

    public static final int MAX_RETRIES = 5;

    /** 重试退避序列（与第 N 次失败后的等待对应，N 从 1 起）。 */
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
            Duration.ofMinutes(5), Duration.ofMinutes(10));

    private Long id;
    private final String businessType;
    private final String businessId;
    private final String operation;
    private final String payload;
    private CompensationStatus status;
    private int retryCount;
    private final int maxRetries;
    private String lastError;
    private Instant nextRetryAt;
    /** CHG-0023：登记时刻请求 traceId（不可变登记事实；历史行/非 HTTP 路径为 null）。 */
    private final String traceId;
    private final Instant createdAt;
    private Instant updatedAt;

    private CompensationTask(Long id, String businessType, String businessId, String operation, String payload,
                             CompensationStatus status, int retryCount, int maxRetries, String lastError,
                             Instant nextRetryAt, String traceId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.businessType = businessType;
        this.businessId = businessId;
        this.operation = operation;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.lastError = lastError;
        this.nextRetryAt = nextRetryAt;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 新登记：PENDING、立即可调度（nextRetryAt=now）；traceId 为触发登记请求的链路标识（可空）。 */
    public static CompensationTask register(String businessType, String businessId, String operation,
                                            String payload, String traceId, Instant now) {
        return new CompensationTask(null, businessType, businessId, operation, payload,
                CompensationStatus.PENDING, 0, MAX_RETRIES, null, now, traceId, now, now);
    }

    /** 持久化重建。 */
    public static CompensationTask reconstitute(Long id, String businessType, String businessId, String operation,
                                                String payload, CompensationStatus status, int retryCount,
                                                int maxRetries, String lastError, Instant nextRetryAt,
                                                String traceId, Instant createdAt, Instant updatedAt) {
        return new CompensationTask(id, businessType, businessId, operation, payload, status, retryCount,
                maxRetries, lastError, nextRetryAt, traceId, createdAt, updatedAt);
    }

    /** 执行成功：置 SUCCESS 终态，清空调度时间。 */
    public void markSuccess(Instant now) {
        this.status = CompensationStatus.SUCCESS;
        this.lastError = null;
        this.nextRetryAt = null;
        this.updatedAt = now;
    }

    /**
     * 执行失败：retryCount+1 并按退避序列安排下一次；达上限置 FAILED_DEAD。
     *
     * @return 是否仍可重试（false 表示已转人工终态）
     */
    public boolean recordFailure(String error, Instant now) {
        this.retryCount++;
        String safeError = error == null ? "unknown error" : error;
        this.lastError = safeError.length() > 1000 ? safeError.substring(0, 1000) : safeError;
        if (retryCount >= maxRetries) {
            this.status = CompensationStatus.FAILED_DEAD;
            this.nextRetryAt = null;
        } else {
            Duration delay = BACKOFF.get(Math.min(retryCount - 1, BACKOFF.size() - 1));
            this.nextRetryAt = now.plus(delay);
        }
        this.updatedAt = now;
        return this.status == CompensationStatus.PENDING;
    }

    /** 管理端手动重试：把 FAILED_DEAD 复活为 PENDING 并立即到期（retryCount 不重置，保留累计次数）。 */
    public void rearmForManualRetry(Instant now) {
        this.status = CompensationStatus.PENDING;
        this.nextRetryAt = now;
        this.updatedAt = now;
    }

    /**
     * 管理端手动标记完成（CHG-0025 STORY-009-05-01，AC-038）：人工确认业务已闭环后直接置 SUCCESS；
     * 不抹除原始 lastError（追加 MANUAL_COMPLETE 标记，与真实成功区分）；SUCCESS 幂等无操作。
     */
    public void manualComplete(Instant now) {
        if (this.status == CompensationStatus.SUCCESS) {
            return;
        }
        String previous = this.lastError == null ? "" : this.lastError.trim();
        this.lastError = previous.isBlank() ? "MANUAL_COMPLETE" : previous + " | MANUAL_COMPLETE";
        this.status = CompensationStatus.SUCCESS;
        this.nextRetryAt = null;
        this.updatedAt = now;
    }

    public void assignPersistedId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String businessType() { return businessType; }
    public String businessId() { return businessId; }
    public String operation() { return operation; }
    public String payload() { return payload; }
    public CompensationStatus status() { return status; }
    public int retryCount() { return retryCount; }
    public int maxRetries() { return maxRetries; }
    public String lastError() { return lastError; }
    public Instant nextRetryAt() { return nextRetryAt; }
    public String traceId() { return traceId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
