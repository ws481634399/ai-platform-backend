package com.ai.mall.order.infrastructure.persistence.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** outbox_event Mapper：CAS 抢占与状态流转用原生 SQL 保证原子性。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventPo> {

    /** PENDING → SENDING（CAS 抢占）。 */
    @Update("UPDATE outbox_event SET status='SENDING', sent_at=#{sentAt} WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    /** SENDING → SENT。 */
    @Update("UPDATE outbox_event SET status='SENT' WHERE id=#{id} AND status='SENDING'")
    int markSent(@Param("id") Long id);

    /** SENDING → FAILED（超限）。 */
    @Update("UPDATE outbox_event SET status='FAILED', last_error=#{lastError} WHERE id=#{id} AND status='SENDING'")
    int markFailed(@Param("id") Long id, @Param("lastError") String lastError);

    /** SENDING → PENDING（退避重投）。 */
    @Update("UPDATE outbox_event SET status='PENDING', retry_count=#{retryCount}, next_retry_at=#{nextRetryAt}, last_error=#{lastError} WHERE id=#{id} AND status='SENDING'")
    int requeueWithBackoff(@Param("id") Long id, @Param("lastError") String lastError,
                           @Param("nextRetryAt") Instant nextRetryAt, @Param("retryCount") int retryCount);

    /** FAILED → PENDING（人工重投，重置计数）。 */
    @Update("UPDATE outbox_event SET status='PENDING', retry_count=0, next_retry_at=NULL, last_error=NULL WHERE id=#{id} AND status='FAILED'")
    int resetForRetry(@Param("id") Long id);
}
