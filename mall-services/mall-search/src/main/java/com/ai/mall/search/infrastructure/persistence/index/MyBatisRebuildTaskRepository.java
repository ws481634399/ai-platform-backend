package com.ai.mall.search.infrastructure.persistence.index;

import com.ai.mall.search.domain.index.IndexRebuildTask;
import com.ai.mall.search.domain.index.RebuildStatus;
import com.ai.mall.search.domain.index.RebuildTaskRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 重建任务仓储 MyBatis-Plus 实现（CHG-0021）。 */
@Repository
public class MyBatisRebuildTaskRepository implements RebuildTaskRepository {

    private final RebuildTaskMapper mapper;

    public MyBatisRebuildTaskRepository(RebuildTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(IndexRebuildTask task) {
        RebuildTaskPo po = toPo(task);
        mapper.insert(po);
        task.setId(po.getId());
    }

    @Override
    public void update(IndexRebuildTask task) {
        mapper.updateById(toPo(task));
    }

    @Override
    public Optional<IndexRebuildTask> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MyBatisRebuildTaskRepository::toDomain);
    }

    @Override
    public Optional<IndexRebuildTask> findRunning() {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<RebuildTaskPo>()
                .eq(RebuildTaskPo::getStatus, RebuildStatus.RUNNING.name())
                .orderByAsc(RebuildTaskPo::getId)
                .last("LIMIT 1"))).map(MyBatisRebuildTaskRepository::toDomain);
    }

    @Override
    public List<IndexRebuildTask> recent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RebuildTaskPo>()
                        .orderByDesc(RebuildTaskPo::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(MyBatisRebuildTaskRepository::toDomain).toList();
    }

    private static RebuildTaskPo toPo(IndexRebuildTask t) {
        RebuildTaskPo po = new RebuildTaskPo();
        po.setId(t.getId());
        po.setTaskNo(t.getTaskNo());
        po.setStatus(t.getStatus().name());
        po.setTotalCount(t.getTotalCount());
        po.setIndexedCount(t.getIndexedCount());
        po.setFailedCount(t.getFailedCount());
        po.setPhysicalIndex(t.getPhysicalIndex());
        po.setErrorMessage(t.getErrorMessage());
        po.setStartedAt(t.getStartedAt());
        po.setFinishedAt(t.getFinishedAt());
        po.setCreatedAt(t.getCreatedAt());
        po.setUpdatedAt(t.getUpdatedAt());
        return po;
    }

    private static IndexRebuildTask toDomain(RebuildTaskPo po) {
        IndexRebuildTask t = new IndexRebuildTask();
        t.setId(po.getId());
        t.setTaskNo(po.getTaskNo());
        t.setStatus(RebuildStatus.valueOf(po.getStatus()));
        t.setTotalCount(po.getTotalCount());
        t.setIndexedCount(po.getIndexedCount());
        t.setFailedCount(po.getFailedCount());
        t.setPhysicalIndex(po.getPhysicalIndex());
        t.setErrorMessage(po.getErrorMessage());
        t.setStartedAt(po.getStartedAt());
        t.setFinishedAt(po.getFinishedAt());
        t.setCreatedAt(po.getCreatedAt());
        t.setUpdatedAt(po.getUpdatedAt());
        return t;
    }
}
