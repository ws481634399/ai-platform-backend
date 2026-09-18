package com.ai.mall.system.application.config;

import com.ai.mall.system.domain.config.ConfigTargetType;

/**
 * 配置变更事件（CHG-0022）：应用服务写库成功后发布，
 * 由缓存失效监听在事务提交后删除 Redis 键（含 public-features 聚合键）。
 *
 * @param targetType FEATURE/PARAMETER
 * @param key        配置键
 */
public record ConfigChangedEvent(ConfigTargetType targetType, String key) {

    public static ConfigChangedEvent feature(String key) {
        return new ConfigChangedEvent(ConfigTargetType.FEATURE, key);
    }

    public static ConfigChangedEvent parameter(String key) {
        return new ConfigChangedEvent(ConfigTargetType.PARAMETER, key);
    }
}
