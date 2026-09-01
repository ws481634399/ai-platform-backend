package com.ai.mall.common.web.exception;

import com.ai.mall.common.core.result.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 {@link ErrorCode} 与 HTTP 语义，由 GlobalExceptionHandler 统一转换。
 *
 * <p>HTTP 状态默认 400；需要 404/409 等语义时由业务方显式指定（design.md §2.3）。
 * message 为面向调用方的文案，禁止携带堆栈/SQL/内部细节。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, HttpStatus.BAD_REQUEST, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus) {
        this(errorCode, httpStatus, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
