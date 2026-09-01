package com.ai.mall.common.web.testsupport;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.common.web.exception.BusinessException;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用 Controller：AC-10 各验证路径的载体（仅 test classpath，非业务功能）。
 */
@RestController
@RequestMapping("/test")
public class TestEchoController {

    /** 正常路径：返回固定数据 */
    @GetMapping("/ok")
    public UnifyResult<String> ok() {
        return UnifyResult.ok("pong");
    }

    /** 日志路径：返回请求处理期间的 MDC traceId（即日志行将打印的值） */
    @GetMapping("/mdc")
    public UnifyResult<String> mdc() {
        return UnifyResult.ok(MDC.get(TraceConstants.MDC_KEY));
    }

    /** 参数校验路径：@Valid @RequestBody 失败 → MethodArgumentNotValidException */
    @PostMapping("/valid")
    public UnifyResult<String> valid(@Valid @RequestBody ValidRequest request) {
        return UnifyResult.ok(request.getName());
    }

    /** 业务异常路径：默认 400 + B 段码 */
    @GetMapping("/biz")
    public UnifyResult<String> biz() {
        throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "库存不足");
    }

    /** 业务异常路径：异常携带 404 语义 */
    @GetMapping("/notfound")
    public UnifyResult<String> notFound() {
        throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, HttpStatus.NOT_FOUND, "商品不存在");
    }

    /** 未预期异常路径：消息故意包含 SQL/类名等内部细节，验证响应体不泄露 */
    @GetMapping("/boom")
    public UnifyResult<String> boom() {
        throw new IllegalStateException("select * from users; jdk internal stack detail");
    }
}
