package com.ai.mall.system.domain.config;

/**
 * 参数生效方式（CHG-0022）：M5 种子全部 DYNAMIC；RESTART_REQUIRED 仅后台展示标记。
 */
public enum EffectType {

    /** 动态生效：消费服务本地缓存 TTL 内（≤60s）感知。 */
    DYNAMIC,
    /** 需重启：仅元数据展示，M5 不接入消费侧热更新。 */
    RESTART_REQUIRED
}
