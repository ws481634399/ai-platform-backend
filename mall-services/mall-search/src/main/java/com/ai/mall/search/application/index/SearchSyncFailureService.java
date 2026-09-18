package com.ai.mall.search.application.index;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.search.domain.index.IndexErrorCode;
import com.ai.mall.search.domain.index.SearchSyncFailure;
import com.ai.mall.search.domain.index.SyncEventType;
import com.ai.mall.search.domain.index.SyncFailureRepository;
import com.ai.mall.search.domain.index.SyncFailureStatus;
import com.ai.mall.search.infrastructure.client.ProductProjectionClient;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 同步失败记录与退避重试（CHG-0021 DU-BE-506）。
 *
 * <p>退避 30s/1m/2m/5m/10m，5 次 FAILED_DEAD；重放时重拉投影当前态（不信旧 payload）；
 * 调度 fixedDelay 30s、每轮 LIMIT 100，单条异常不影响批次（单实例前提见主类注释）。
 */
@Service
public class SearchSyncFailureService {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncFailureService.class);
    private static final int DUE_LIMIT = 100;

    private final SyncFailureRepository repository;
    private final ProductProjectionClient projectionClient;
    private final SyncWriteExecutor writeExecutor;

    public SearchSyncFailureService(SyncFailureRepository repository,
                                    ProductProjectionClient projectionClient,
                                    @Lazy SyncWriteExecutor writeExecutor) {
        this.repository = repository;
        this.projectionClient = projectionClient;
        this.writeExecutor = writeExecutor;
    }

    /** 登记/复用失败记录；落库自身失败仅 ERROR，不影响受理语义。 */
    public void recordFailure(long productId, SyncEventType eventType, String reason) {
        try {
            Instant now = Instant.now();
            repository.findPending(productId, eventType).ifPresentOrElse(
                    existing -> {
                        existing.refresh(reason, now);
                        repository.update(existing);
                    },
                    () -> repository.insert(SearchSyncFailure.register(productId, eventType, reason, now)));
        } catch (Exception ex) {
            log.error("同步失败记录落库异常 productId={} type={}", productId, eventType, ex);
        }
    }

    /** 每 30s 扫描到期 PENDING。 */
    @Scheduled(fixedDelayString = "${mall.search.sync-retry-delay-ms:30000}")
    public void scanAndRetry() {
        List<SearchSyncFailure> due;
        try {
            due = repository.findDue(DUE_LIMIT, Instant.now());
        } catch (Exception ex) {
            log.error("捞取到期同步失败记录异常", ex);
            return;
        }
        for (SearchSyncFailure failure : due) {
            replay(failure);
        }
    }

    /** 重放一条：重拉当前投影，在售 upsert / 不可见 delete，落最终结果。 */
    public void replay(SearchSyncFailure failure) {
        Instant now = Instant.now();
        try {
            var projection = projectionClient.fetchOne(failure.getProductId());
            if (projection == null) {
                writeExecutor.deletePlain(failure.getProductId());
            } else {
                writeExecutor.upsertLatest(projection);
            }
            failure.markSuccess(now);
            log.info("同步失败重试成功 id={} productId={} type={}",
                    failure.getId(), failure.getProductId(), failure.getEventType());
        } catch (Exception ex) {
            failure.recordRetryFailure(rootMessage(ex), now);
            log.warn("同步失败重试退避 id={} productId={} retry={} status={}",
                    failure.getId(), failure.getProductId(), failure.getRetryCount(), failure.getStatus(), ex);
        }
        try {
            repository.update(failure);
        } catch (Exception updateEx) {
            log.error("同步失败结果落库异常 id={}", failure.getId(), updateEx);
        }
    }

    /** 人工重试：复活记录并立即执行一次。 */
    public SearchSyncFailure manualRetry(long id) {
        SearchSyncFailure failure = repository.findById(id)
                .orElseThrow(() -> new BusinessException(IndexErrorCode.SYNC_FAILURE_NOT_FOUND,
                        HttpStatus.NOT_FOUND));
        if (failure.getStatus() != SyncFailureStatus.PENDING) {
            failure.rearm(Instant.now());
            repository.update(failure);
        }
        replay(failure);
        return repository.findById(id).orElse(failure);
    }

    public List<SearchSyncFailure> page(String status, int page, int size) {
        return repository.page(status, page, size);
    }

    public long countByStatus(String status) {
        return repository.count(status);
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
