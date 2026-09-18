package com.ai.mall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 功能开关门面（CHG-0022）：业务代码唯一入口。
 *
 * <p>语义冻结：
 * <ul>
 *   <li>键存在：按 enabled 决策；</li>
 *   <li>键缺失/配置链路全失：按调用方传入的默认值决策（搜索/游客车等已有能力传 true，避免回归）；</li>
 *   <li>{@link #ensureEnabled} 仅在“显式关闭”时抛 {@link FeatureDisabledException}（B0606/403），
 *       缺键默认放行。</li>
 * </ul>
 */
public class FeatureGate {

    private static final Logger log = LoggerFactory.getLogger(FeatureGate.class);

    private final SystemConfigClient client;

    public FeatureGate(SystemConfigClient client) {
        this.client = client;
    }

    /** 缺省默认启用（已有能力不因配置基础设施故障而中断）。 */
    public boolean isEnabled(String key) {
        return isEnabled(key, true);
    }

    public boolean isEnabled(String key, boolean defaultWhenMissing) {
        return client.getFeature(key)
                .map(FeatureSnapshot::enabled)
                .orElseGet(() -> {
                    log.warn("功能开关缺键或读取失败，按默认值决策 key={} default={}", key, defaultWhenMissing);
                    return defaultWhenMissing;
                });
    }

    /** 缺键/故障默认放行；仅显式 false 拒绝（B0606）。 */
    public void ensureEnabled(String key) {
        ensureEnabled(key, true);
    }

    public void ensureEnabled(String key, boolean defaultWhenMissing) {
        if (!isEnabled(key, defaultWhenMissing)) {
            throw new FeatureDisabledException(key);
        }
    }
}
