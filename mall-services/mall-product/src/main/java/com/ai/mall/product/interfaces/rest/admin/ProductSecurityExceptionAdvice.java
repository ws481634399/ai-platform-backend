package com.ai.mall.product.interfaces.rest.admin;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级鉴权拒绝（@PreAuthorize 抛出的 AccessDeniedException）统一 403。
 *
 * <p>FilterChain 上的 URL 级拒绝由 security 链的 accessDeniedHandler 处理；
 * 但方法拦截器在 Controller 代理调用处抛出的拒绝异常发生在过滤链之后，
 * 会落入全局异常处理，故在此显式映射为 403 + 统一响应体。
 */
@RestControllerAdvice
public class ProductSecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public UnifyResult<Void> handleAccessDenied(AccessDeniedException ex) {
        return UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied");
    }
}
