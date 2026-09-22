package com.ai.mall.event;

/**
 * 事件版本矩阵（单一事实源）。
 * <p>演进规则：新增字段=当前版本内兼容（消费端忽略未知字段）；破坏性变更才升版本，
 * 消费端 eventVersion 大于 maxSupportedVersion 时拒绝（ACK+WARN，不按旧版本解析）。</p>
 */
public final class EventVersions {

    /** 当前 schema 版本：五个事件均从 v1 起步 */
    public static final int CURRENT = 1;
    /** 消费端默认最大支持版本（@IntegrationEventListener 可按事件覆写） */
    public static final int MAX_SUPPORTED = 1;

    private EventVersions() {
    }
}
