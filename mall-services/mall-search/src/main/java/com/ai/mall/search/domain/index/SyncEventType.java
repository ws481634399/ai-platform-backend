package com.ai.mall.search.domain.index;

/** 同步事件类型：上架/变更→UPSERT；下架/删除→DELETE。 */
public enum SyncEventType {
    UPSERT,
    DELETE
}
