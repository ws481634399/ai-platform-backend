package com.ai.mall.search.domain.search;

import com.ai.mall.common.web.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 搜索域业务异常：参数非法由应用层直接以 {@link SearchErrorCode#SEARCH_BAD_REQUEST}
 * 构造；ES 故障统一在 SearchExceptionAdvice 按异常类型映射，不要求业务方手工抛出。
 */
public class SearchException extends BusinessException {

    public SearchException(SearchErrorCode errorCode) {
        super(errorCode, errorCode == SearchErrorCode.SEARCH_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST);
    }

    public SearchException(SearchErrorCode errorCode, String message) {
        super(errorCode, errorCode == SearchErrorCode.SEARCH_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST, message);
    }
}
