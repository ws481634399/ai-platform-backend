package com.ai.mall.identity.interfaces.rest;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将方法级授权拒绝稳定映射为统一 403 响应。 */
@RestControllerAdvice
public class IdentityAccessDeniedExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<UnifyResult<Void>> handle(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied"));
    }
}
