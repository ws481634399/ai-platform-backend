package com.ai.mall.system.interfaces.rest.admin;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级鉴权拒绝（@PreAuthorize 抛出的 AccessDeniedException）统一 403（CHG-0022）。
 */
@RestControllerAdvice
public class SystemSecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public UnifyResult<Void> handleAccessDenied(AccessDeniedException ex) {
        return UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied");
    }
}
