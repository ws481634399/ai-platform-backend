package com.ai.mall.system.infrastructure.cache;

/** 公开功能开关视图（仅 key+enabled）。 */
public record PublicFeatureView(String key, boolean enabled) {
}
