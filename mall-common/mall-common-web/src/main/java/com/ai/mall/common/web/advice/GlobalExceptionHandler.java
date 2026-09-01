package com.ai.mall.common.web.advice;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 全局异常处理：一切异常的响应体统一为 {@link UnifyResult}。
 *
 * <p>转换规则（design.md §2.3）：
 * <ul>
 *   <li>参数校验异常（MethodArgumentNotValidException 为 BindException 子类）→ 400 + A 段码</li>
 *   <li>BusinessException → 异常携带的业务码与 HTTP 语义</li>
 *   <li>未预期异常 → 500 + S 段通用文案；服务端仅日志留痕，响应体禁止泄露堆栈/SQL/内部细节</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数校验异常 → 400 + A 段码（字段级错误文案） */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public UnifyResult<Void> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
        String message = detail.isEmpty() ? CommonErrorCode.PARAM_INVALID.getMessage() : detail;
        return UnifyResult.fail(CommonErrorCode.PARAM_INVALID, message);
    }

    /** 业务异常 → 异常携带的业务码与 HTTP 语义（400/404/409 等） */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<UnifyResult<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(UnifyResult.fail(ex.getErrorCode(), ex.getMessage()));
    }

    /** 未预期异常 → 500 + S 段通用文案；服务端日志留痕，响应体不泄露内部信息 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public UnifyResult<Void> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return UnifyResult.fail(CommonErrorCode.SYSTEM_ERROR);
    }
}
