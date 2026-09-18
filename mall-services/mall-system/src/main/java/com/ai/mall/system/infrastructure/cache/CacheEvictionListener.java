package com.ai.mall.system.infrastructure.cache;

import com.ai.mall.system.application.config.ConfigChangedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 配置变更缓存失效监听（CHG-0022）：仅在事务提交后删 Redis 键。
 *
 * <p>AFTER_COMMIT 保证 DB 回滚时不会误删（虽误删也只是下次回源重建，但保持语义干净）；
 * 删键异常已在 ConfigCacheService 内吞掉（TTL 兜底），不影响提交结果。
 */
@Component
public class CacheEvictionListener {

    private final ConfigCacheService cacheService;

    public CacheEvictionListener(ConfigCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigChanged(ConfigChangedEvent event) {
        cacheService.evict(event.targetType(), event.key());
    }
}
