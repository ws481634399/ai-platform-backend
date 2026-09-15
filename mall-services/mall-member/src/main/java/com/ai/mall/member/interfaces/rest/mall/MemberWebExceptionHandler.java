package com.ai.mall.member.interfaces.rest.mall;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.member.application.member.MemberProfileErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * mall-member Web 异常补充处理（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>最高优先级先于 common-web GlobalExceptionHandler 收口：
 * <ul>
 *   <li>容器层 {@code spring.servlet.multipart.max-file-size=2MB} 触发的
 *       {@link MaxUploadSizeExceededException} → 400 FILE_TOO_LARGE
 *       （应用层对字节还有一次等价显式校验，MockMvc 等绕过容器解析的路径同样拦截）；</li>
 *   <li>方法级 {@code @PreAuthorize} 拒绝（{@link AccessDeniedException}，6.4 起实际为
 *       AuthorizationDeniedException）→ 403，避免被通用兜底吞成 500；与路径层 hasRole 等价。</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MemberWebExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<UnifyResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(UnifyResult.fail(MemberProfileErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<UnifyResult<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied"));
    }
}
