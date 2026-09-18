package com.ai.mall.search.interfaces.rest.admin;

import com.ai.mall.search.domain.index.SearchSyncFailure;
import java.time.Instant;

/** 管理端同步失败记录视图。 */
public record SyncFailureView(
        Long id,
        Long productId,
        String eventType,
        String status,
        Integer retryCount,
        Integer maxRetries,
        String lastError,
        Long nextRetryAt,
        Long createdAt) {

    public static SyncFailureView from(SearchSyncFailure f) {
        return new SyncFailureView(
                f.getId(), f.getProductId(), f.getEventType().name(), f.getStatus().name(),
                f.getRetryCount(), f.getMaxRetries(), f.getLastError(),
                millis(f.getNextRetryAt()), millis(f.getCreatedAt()));
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
