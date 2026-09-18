package com.ai.mall.system.domain.config;

import java.time.Instant;

/**
 * 配置变更历史（CHG-0022）：只追加，不可修改、不可删除。
 */
public record ConfigHistory(
        Long id,
        ConfigTargetType configType,
        String configKey,
        String oldValue,
        String newValue,
        ChangeKind changeKind,
        String changedBy,
        String changeReason,
        String traceId,
        Instant createdAt) {

    public static ConfigHistory recorded(ConfigTargetType type, String key, String oldValue, String newValue,
                                         ChangeKind kind, String changedBy, String changeReason,
                                         String traceId, Instant now) {
        return new ConfigHistory(null, type, key, oldValue, newValue, kind,
                changedBy, changeReason, traceId, now);
    }
}
