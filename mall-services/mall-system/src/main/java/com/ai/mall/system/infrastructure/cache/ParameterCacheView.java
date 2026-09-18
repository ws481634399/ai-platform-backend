package com.ai.mall.system.infrastructure.cache;

/**
 * 内部端点参数视图（HTTP 契约：configValue/parameterType 全名，CHG-0022 story-design §2 冻结）。
 */
public record ParameterCacheView(String key, String configValue, String parameterType,
                                 String minValue, String maxValue, long version) {
}
