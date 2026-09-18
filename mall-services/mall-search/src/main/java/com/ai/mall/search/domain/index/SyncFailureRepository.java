package com.ai.mall.search.domain.index;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 同步失败记录仓储端口。 */
public interface SyncFailureRepository {

    void insert(SearchSyncFailure failure);

    void update(SearchSyncFailure failure);

    Optional<SearchSyncFailure> findById(long id);

    /** 同商品同事件的 PENDING 记录（应用层复用语义）。 */
    Optional<SearchSyncFailure> findPending(long productId, SyncEventType eventType);

    /** 到期 PENDING：next_retry_at <= now，上限 limit。 */
    List<SearchSyncFailure> findDue(int limit, Instant now);

    long count(String status);

    List<SearchSyncFailure> page(String status, int page, int size);
}
