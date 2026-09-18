package com.ai.mall.system.infrastructure.cache;

/** 内部端点/缓存中的功能开关视图。 */
public record FeatureCacheView(String key, boolean enabled, long version) {
}
