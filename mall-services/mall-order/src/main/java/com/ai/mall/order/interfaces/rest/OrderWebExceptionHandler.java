package com.ai.mall.order.interfaces.rest;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * mall-order Web 异常补充处理（CHG-0019）。
 *
 * <p>最高优先级先于 common-web GlobalExceptionHandler：方法级 {@code @PreAuthorize}
 * 拒绝（Spring Security 6.4 起实际为 AuthorizationDeniedException）→ 403，
 * 避免被通用兜底吞成 500/400；与路径层 hasRole 语义一致。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderWebExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<UnifyResult<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied"));
    }
}
