package com.ai.mall.search.domain.search;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 搜索域错误码（B05xx，CHG-0020）。
 *
 * <ul>
 *   <li>B0501 SEARCH_UNAVAILABLE：Elasticsearch 不可用/超时/索引不存在（503）</li>
 *   <li>B0502 SEARCH_BAD_REQUEST：搜索参数非法（400）</li>
 * </ul>
 */
public enum SearchErrorCode implements ErrorCode {

    SEARCH_UNAVAILABLE("B0501", "搜索服务暂时不可用，请稍后重试"),
    SEARCH_BAD_REQUEST("B0502", "搜索参数不合法");

    private final String code;
    private final String message;

    SearchErrorCode(String code, String message) {
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
