package com.ai.mall.search.interfaces.rest.admin;

import com.ai.mall.search.domain.index.IndexRebuildTask;
import com.ai.mall.search.domain.index.SearchSyncFailure;
import java.time.Instant;

/** 管理端索引重建任务视图（时间统一 epoch millis）。 */
public record RebuildTaskView(
        Long id,
        String taskNo,
        String status,
        Integer totalCount,
        Integer indexedCount,
        Integer failedCount,
        String physicalIndex,
        String errorMessage,
        Long startedAt,
        Long finishedAt,
        Long createdAt) {

    public static RebuildTaskView from(IndexRebuildTask t) {
        return new RebuildTaskView(
                t.getId(), t.getTaskNo(), t.getStatus().name(),
                t.getTotalCount(), t.getIndexedCount(), t.getFailedCount(),
                t.getPhysicalIndex(), t.getErrorMessage(),
                millis(t.getStartedAt()), millis(t.getFinishedAt()), millis(t.getCreatedAt()));
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
