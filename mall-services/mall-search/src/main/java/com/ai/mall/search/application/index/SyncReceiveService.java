package com.ai.mall.search.application.index;

import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.domain.search.SearchProductDocument;
import com.ai.mall.search.domain.index.SearchIndexPort;
import com.ai.mall.search.domain.index.SearchIndexPort.IndexWriteResult;
import com.ai.mall.search.domain.index.SyncEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 同步写入受理（CHG-0021 DU-BE-505/506）。
 *
 * <p>ES 写入异常一律登记 failure_record 后正常返回（调用方 200 受理）；
 * external_gte 版本冲突（乱序/重复）按成功消化：过期事件 INFO 留痕、不落失败表。
 */
@Service
public class SyncReceiveService implements SyncWriteExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncReceiveService.class);

    private static final String STATUS_ON_SALE = SearchProductDocument.STATUS_ON_SALE;

    private final SearchIndexPort searchIndexPort;
    private final SearchSyncFailureService failureService;

    public SyncReceiveService(SearchIndexPort searchIndexPort,
                              SearchSyncFailureService failureService) {
        this.searchIndexPort = searchIndexPort;
        this.failureService = failureService;
    }

    /**
     * 受理一条投影：ON_SALE 版本化 upsert；其余状态版本化删除（下架）。
     * ES 故障落失败表，不抛给调用方。
     */
    public void receive(ProductProjectionView view) {
        long productId = Long.parseLong(view.productId());
        SyncEventType type = STATUS_ON_SALE.equals(view.status()) ? SyncEventType.UPSERT : SyncEventType.DELETE;
        try {
            IndexWriteResult result = STATUS_ON_SALE.equals(view.status())
                    ? searchIndexPort.upsertVersioned(view)
                    : searchIndexPort.deleteVersioned(view);
            if (result == IndexWriteResult.STALE_VERSION) {
                log.info("过期同步事件已消化 productId={} type={} version={}",
                        productId, type, view.updatedAt());
            }
        } catch (Exception ex) {
            log.warn("ES 写入失败，登记同步失败记录 productId={} type={}", productId, type, ex);
            failureService.recordFailure(productId, type, rootMessage(ex));
        }
    }

    /** 内部硬删除端点（下架事件通道）。 */
    public void receiveDelete(long productId) {
        try {
            searchIndexPort.deletePlain(productId);
        } catch (Exception ex) {
            log.warn("ES 删除失败，登记同步失败记录 productId={}", productId, ex);
            failureService.recordFailure(productId, SyncEventType.DELETE, rootMessage(ex));
        }
    }

    @Override
    public void upsertLatest(ProductProjectionView view) {
        IndexWriteResult result = searchIndexPort.upsertVersioned(view);
        if (result == IndexWriteResult.STALE_VERSION) {
            log.info("重试时发现更新版本已存在，按成功处理 productId={}", view.productId());
        }
    }

    @Override
    public void deletePlain(long productId) {
        searchIndexPort.deletePlain(productId);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
