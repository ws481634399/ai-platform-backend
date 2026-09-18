package com.ai.mall.search.domain.index;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 索引域错误码（CHG-0021）。
 */
public enum IndexErrorCode implements ErrorCode {

    SEARCH_REBUILD_RUNNING("B0503", "已有重建任务执行中，请稍后再试"),
    SYNC_FAILURE_NOT_FOUND("B0504", "同步失败记录不存在");

    private final String code;
    private final String message;

    IndexErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
