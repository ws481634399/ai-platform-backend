package com.ai.mall.order.infrastructure.persistence.compensation;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

import com.ai.mall.order.domain.compensation.CompensationRepository;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 补偿任务仓储 MyBatis-Plus 实现（CHG-0019 REQ-M4-004）。 */
@Repository
public class MyBatisCompensationRepository implements CompensationRepository {

    private final CompensationTaskMapper mapper;

    public MyBatisCompensationRepository(CompensationTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean insertIgnore(CompensationTask task) {
        CompensationTaskPo po = toPo(task);
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException duplicate) {
            // 同业务单同操作已登记：复用既有任务，保证登记幂等
            return false;
        }
        task.assignPersistedId(po.getId());
        return true;
    }

    @Override
    public List<CompensationTask> findDue(int limit, Instant now) {
        return mapper.selectList(lambdaQuery(CompensationTaskPo.class)
                        .eq(CompensationTaskPo::getStatus, CompensationStatus.PENDING.name())
                        .le(CompensationTaskPo::getNextRetryAt, now)
                        .orderByAsc(CompensationTaskPo::getNextRetryAt)
                        .last("LIMIT " + limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void update(CompensationTask task) {
        if (task.getId() == null) {
            throw new IllegalArgumentException("补偿任务未持久化，无法更新");
        }
        mapper.updateById(toPo(task));
    }

    @Override
    public Optional<CompensationTask> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public CompensationPage page(String status, int page, int size) {
        var wrapper = lambdaQuery(CompensationTaskPo.class)
                .eq(status != null && !status.isBlank(), CompensationTaskPo::getStatus, status)
                .orderByDesc(CompensationTaskPo::getCreatedAt);
        Page<CompensationTaskPo> poPage = mapper.selectPage(new Page<>(page, size), wrapper);
        List<CompensationTask> records = poPage.getRecords().stream().map(this::toDomain).toList();
        return new CompensationPage(records, poPage.getTotal(), page, size);
    }

    private CompensationTaskPo toPo(CompensationTask domain) {
        CompensationTaskPo po = new CompensationTaskPo();
        po.setId(domain.getId());
        po.setBusinessType(domain.businessType());
        po.setBusinessId(domain.businessId());
        po.setOperation(domain.operation());
        po.setPayload(domain.payload());
        po.setStatus(domain.status().name());
        po.setRetryCount(domain.retryCount());
        po.setMaxRetries(domain.maxRetries());
        po.setLastError(domain.lastError());
        po.setNextRetryAt(domain.nextRetryAt());
        po.setCreatedAt(domain.createdAt());
        po.setUpdatedAt(domain.updatedAt());
        return po;
    }

    private CompensationTask toDomain(CompensationTaskPo po) {
        return CompensationTask.reconstitute(po.getId(), po.getBusinessType(), po.getBusinessId(),
                po.getOperation(), po.getPayload(), CompensationStatus.valueOf(po.getStatus()),
                po.getRetryCount() == null ? 0 : po.getRetryCount(),
                po.getMaxRetries() == null ? CompensationTask.MAX_RETRIES : po.getMaxRetries(),
                po.getLastError(), po.getNextRetryAt(), po.getCreatedAt(), po.getUpdatedAt());
    }
}
