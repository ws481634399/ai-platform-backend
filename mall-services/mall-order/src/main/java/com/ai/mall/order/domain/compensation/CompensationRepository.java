package com.ai.mall.order.domain.compensation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 补偿任务仓储端口（CHG-0019 REQ-M4-004）。
 *
 * <p>登记依赖 {@code (business_type, business_id, operation)} 唯一键幂等：
 * 并发/重复登记只保留首条。
 */
public interface CompensationRepository {

    /** 幂等登记：唯一键冲突时保留既有任务（返回 false）。 */
    boolean insertIgnore(CompensationTask task);

    /** 调度捞取：PENDING 且 next_retry_at 到期，按到期时间升序限量。 */
    List<CompensationTask> findDue(int limit, Instant now);

    /** 全量更新（状态/次数/错误/下次调度时间）。 */
    void update(CompensationTask task);

    Optional<CompensationTask> findById(long id);

    /** 管理台分页（status 可空）。 */
    CompensationPage page(String status, int page, int size);

    record CompensationPage(List<CompensationTask> records, long total, int page, int size) {
    }
}
