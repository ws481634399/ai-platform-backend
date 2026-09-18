package com.ai.mall.product.application.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.ai.mall.product.infrastructure.client.SearchSyncClient;

/**
 * 商品搜索同步监听器（CHG-0021）。
 *
 * <p>AFTER_COMMIT 触发：事务未提交绝不发同步；重查当前投影，
 * 存在 → POST /sync（search 按 status 决定 upsert/版本化删除），
 * 不存在 → DELETE /{id}（下架/停用/删除/无启用 SKU 统一口径）。
 *
 * <p>全兜底：任何异常仅 ERROR（productId/op/traceId），不外抛，
 * 绝不影响商品写接口返回；search 不可用窗口由其失败退避表与一致性检查兜底。
 */
@Component
public class ProductSearchSyncListener {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchSyncListener.class);

    private final ProductSearchProjectionService projectionService;
    private final SearchSyncClient searchSyncClient;

    public ProductSearchSyncListener(ProductSearchProjectionService projectionService,
                                     SearchSyncClient searchSyncClient) {
        this.projectionService = projectionService;
        this.searchSyncClient = searchSyncClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductSearchChangedEvent event) {
        String traceId = MDC.get("traceId");
        try {
            SearchProjectionView view = projectionService.findById(event.productId());
            if (view != null) {
                searchSyncClient.sync(view);
                log.info("商品搜索同步完成 productId={} op={} action=sync",
                        event.productId(), event.operation());
            } else {
                searchSyncClient.delete(event.productId());
                log.info("商品搜索同步完成 productId={} op={} action=delete",
                        event.productId(), event.operation());
            }
        } catch (Exception ex) {
            // search 端故障已在其侧落失败表；网络不可达窗口由一致性检查兜底，此处仅留痕
            log.error("商品搜索同步失败（不影响主流程） productId={} op={} traceId={}",
                    event.productId(), event.operation(), traceId, ex);
        }
    }
}
