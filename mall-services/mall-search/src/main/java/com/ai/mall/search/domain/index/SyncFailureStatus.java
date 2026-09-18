package com.ai.mall.search.domain.index;

/** 同步失败记录状态：PENDING 调度拾取；SUCCESS 终态；FAILED_DEAD 等待人工重试。 */
public enum SyncFailureStatus {
    PENDING,
    SUCCESS,
    FAILED_DEAD
}
