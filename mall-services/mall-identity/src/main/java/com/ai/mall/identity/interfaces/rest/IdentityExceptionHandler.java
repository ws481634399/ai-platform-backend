package com.ai.mall.identity.interfaces.rest;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.ai.mall.identity.interfaces")
public class IdentityExceptionHandler {
    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<UnifyResult<Void>> handle(UseCaseException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        CommonErrorCode code = exception.kind() == UseCaseException.Kind.INVALID
                ? CommonErrorCode.PARAM_INVALID : CommonErrorCode.BUSINESS_ERROR;
        return ResponseEntity.status(status).body(UnifyResult.fail(code, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<UnifyResult<Void>> invalidDomainCommand(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(UnifyResult.fail(CommonErrorCode.PARAM_INVALID, exception.getMessage()));
    }

    @ExceptionHandler(DomainRuleViolation.class)
    public ResponseEntity<UnifyResult<Void>> rejectedDomainTransition(DomainRuleViolation exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, exception.getMessage()));
    }
}
