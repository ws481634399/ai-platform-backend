package com.ai.mall.search.domain.index;

/** 重建任务状态机：RUNNING → SUCCESS / FAILED（并发闸门只认 RUNNING）。 */
public enum RebuildStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
