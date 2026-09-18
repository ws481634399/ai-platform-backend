package com.ai.mall.search.domain.index;

import java.util.List;
import java.util.Optional;

/** 重建任务仓储端口。 */
public interface RebuildTaskRepository {

    void insert(IndexRebuildTask task);

    void update(IndexRebuildTask task);

    Optional<IndexRebuildTask> findById(long id);

    Optional<IndexRebuildTask> findRunning();

    List<IndexRebuildTask> recent(int limit);
}
