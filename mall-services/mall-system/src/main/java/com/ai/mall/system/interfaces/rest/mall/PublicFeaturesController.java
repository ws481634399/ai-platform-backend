package com.ai.mall.system.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.system.infrastructure.cache.ConfigCacheService;
import com.ai.mall.system.infrastructure.cache.PublicFeatureView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开功能开关端点（CHG-0022）：匿名可访问，只返回 publicFlag=1 的 {key,enabled}。
 * 不含参数值、分组等任何元信息；读 Redis 聚合键（未命中回源回填）。
 */
@RestController
@RequestMapping("/api/mall")
public class PublicFeaturesController {

    private final ConfigCacheService cacheService;

    public PublicFeaturesController(ConfigCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/public-features")
    public UnifyResult<List<PublicFeatureView>> publicFeatures() {
        return UnifyResult.ok(cacheService.publicFeatures());
    }
}
