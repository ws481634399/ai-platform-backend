package com.ai.mall.search.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.search.application.index.SyncReceiveService;
import com.ai.mall.search.application.index.projection.ProductProjectionView;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mall-product → mall-search 内部同步入口（CHG-0021）。
 *
 * <p>仅接受 X-Internal-Token（安全链 hasRole SERVICE）；ES 故障语义为受理成功（accepted:true），
 * 失败落 search_sync_failure_record 由退避任务消化，不阻塞 product 本地事务返回。
 */
@RestController
@RequestMapping("/api/internal/search/products")
public class InternalSearchSyncController {

    private final SyncReceiveService syncReceiveService;

    public InternalSearchSyncController(SyncReceiveService syncReceiveService) {
        this.syncReceiveService = syncReceiveService;
    }

    /** 投影变更（upsert/状态变化后的当前态）。 */
    @PostMapping("/sync")
    public UnifyResult<Map<String, Object>> sync(@RequestBody ProductProjectionView view) {
        syncReceiveService.receive(view);
        return UnifyResult.ok(Map.of("accepted", true));
    }

    /** 下架/删除硬删文档。 */
    @DeleteMapping("/{productId}")
    public UnifyResult<Map<String, Object>> delete(@PathVariable long productId) {
        syncReceiveService.receiveDelete(productId);
        return UnifyResult.ok(Map.of("accepted", true));
    }
}
