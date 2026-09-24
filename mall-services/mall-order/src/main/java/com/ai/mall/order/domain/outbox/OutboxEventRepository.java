package com.ai.mall.order.domain.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 事件仓储端口。
 *
 * <p>CAS 抢占（claim）与状态流转由实现层用 SQL 保证原子性，多实例并发安全。</p>
 */
public interface OutboxEventRepository {

    /** 持久化一条 PENDING 事件（调用方事务内执行）。 */
    void save(OutboxEvent event);

    /** 拉取到期待投递记录，按 aggregate_id、created_at 升序（同聚合顺序由调用方分组取首条）。 */
    List<OutboxEvent> findPendingDue(int limit, Instant now);

    /**
     * CAS 抢占：将 PENDING 置为 SENDING 并写 sent_at。
     *
     * @return 影响行数（1=抢占成功，0=被其他实例抢走或状态已变）
     */
    int claim(Long id, Instant sentAt);

    /** 抢占成功且发送完成：SENDING → SENT。 */
    int markSent(Long id);

    /** 持续失败超限：SENDING → FAILED，记录失败原因。 */
    int markFailed(Long id, String lastError);

    /** 失败但未超限：SENDING → PENDING，递增重试次数并按退避推迟下次投递。 */
    int requeueWithBackoff(Long id, String lastError, Instant nextRetryAt, int retryCount);

    /** 人工重投：FAILED → PENDING，重置重试次数与下次投递时间。 */
    int resetForRetry(Long id);

    Optional<OutboxEvent> findById(Long id);

    OutboxPage page(String status, String eventType, String aggregateId, int page, int size);

    record OutboxPage(List<OutboxEvent> records, long total, int page, int size) {
    }
}
