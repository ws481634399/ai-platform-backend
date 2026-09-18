package com.ai.mall.search.infrastructure.persistence.index;

import com.ai.mall.search.domain.index.SearchSyncFailure;
import com.ai.mall.search.domain.index.SyncEventType;
import com.ai.mall.search.domain.index.SyncFailureRepository;
import com.ai.mall.search.domain.index.SyncFailureStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 同步失败记录仓储 MyBatis-Plus 实现（CHG-0021）。 */
@Repository
public class MyBatisSyncFailureRepository implements SyncFailureRepository {

    private final SyncFailureMapper mapper;

    public MyBatisSyncFailureRepository(SyncFailureMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(SearchSyncFailure failure) {
        SyncFailurePo po = toPo(failure);
        mapper.insert(po);
        failure.setId(po.getId());
    }

    @Override
    public void update(SearchSyncFailure failure) {
        mapper.updateById(toPo(failure));
    }

    @Override
    public Optional<SearchSyncFailure> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MyBatisSyncFailureRepository::toDomain);
    }

    @Override
    public Optional<SearchSyncFailure> findPending(long productId, SyncEventType eventType) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<SyncFailurePo>()
                .eq(SyncFailurePo::getProductId, productId)
                .eq(SyncFailurePo::getEventType, eventType.name())
                .eq(SyncFailurePo::getStatus, SyncFailureStatus.PENDING.name())
                .orderByAsc(SyncFailurePo::getId)
                .last("LIMIT 1"))).map(MyBatisSyncFailureRepository::toDomain);
    }

    @Override
    public List<SearchSyncFailure> findDue(int limit, Instant now) {
        return mapper.selectList(new LambdaQueryWrapper<SyncFailurePo>()
                        .eq(SyncFailurePo::getStatus, SyncFailureStatus.PENDING.name())
                        .le(SyncFailurePo::getNextRetryAt, now)
                        .orderByAsc(SyncFailurePo::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream().map(MyBatisSyncFailureRepository::toDomain).toList();
    }

    @Override
    public long count(String status) {
        LambdaQueryWrapper<SyncFailurePo> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(SyncFailurePo::getStatus, status.trim());
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public List<SearchSyncFailure> page(String status, int page, int size) {
        LambdaQueryWrapper<SyncFailurePo> wrapper = new LambdaQueryWrapper<SyncFailurePo>()
                .orderByDesc(SyncFailurePo::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SyncFailurePo::getStatus, status.trim());
        }
        return mapper.selectPage(new Page<>(page, size), wrapper)
                .getRecords().stream().map(MyBatisSyncFailureRepository::toDomain).toList();
    }

    private static SyncFailurePo toPo(SearchSyncFailure f) {
        SyncFailurePo po = new SyncFailurePo();
        po.setId(f.getId());
        po.setProductId(f.getProductId());
        po.setEventType(f.getEventType().name());
        po.setStatus(f.getStatus().name());
        po.setRetryCount(f.getRetryCount());
        po.setMaxRetries(f.getMaxRetries());
        po.setLastError(f.getLastError());
        po.setNextRetryAt(f.getNextRetryAt());
        po.setCreatedAt(f.getCreatedAt());
        po.setUpdatedAt(f.getUpdatedAt());
        return po;
    }

    private static SearchSyncFailure toDomain(SyncFailurePo po) {
        SearchSyncFailure f = new SearchSyncFailure();
        f.setId(po.getId());
        f.setProductId(po.getProductId());
        f.setEventType(SyncEventType.valueOf(po.getEventType()));
        f.setStatus(SyncFailureStatus.valueOf(po.getStatus()));
        f.setRetryCount(po.getRetryCount());
        f.setMaxRetries(po.getMaxRetries());
        f.setLastError(po.getLastError());
        f.setNextRetryAt(po.getNextRetryAt());
        f.setCreatedAt(po.getCreatedAt());
        f.setUpdatedAt(po.getUpdatedAt());
        return f;
    }
}
