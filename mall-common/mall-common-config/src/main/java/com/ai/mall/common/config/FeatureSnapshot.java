package com.ai.mall.common.config;

/**
 * 功能开关快照（Redis/HTTP 跨进程 JSON 契约，CHG-0022 §2.3 冻结）。
 *
 * @param key     开关键
 * @param enabled 是否启用
 * @param version 配置版本（乐观锁/排查用）
 */
public record FeatureSnapshot(String key, boolean enabled, long version) {
}
