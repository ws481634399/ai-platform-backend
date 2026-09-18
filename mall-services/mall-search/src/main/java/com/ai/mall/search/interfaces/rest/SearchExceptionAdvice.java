package com.ai.mall.search.interfaces.rest;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.transport.TransportException;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.search.domain.search.SearchErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 搜索异常统一口径（CHG-0020 DU-BE-510）。
 *
 * <p>优先级高于 common-web 兜底：ES 连接拒绝/超时/索引缺失统一 503 B0501；
 * 搜索参数非法统一 400 B0502。WARN 日志携带 traceId；响应体不含主机/堆栈。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(SearchExceptionAdvice.class);

    @ExceptionHandler({ElasticsearchException.class, TransportException.class,
            ResponseException.class, IOException.class})
    public ResponseEntity<UnifyResult<Void>> handleElasticsearch(Exception ex) throws Exception {
        if (isSearchUnavailable(ex)) {
            return unavailable(ex);
        }
        // 非连接类 ES 异常不吞并：交还 common-web 兜底 500
        throw ex;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<UnifyResult<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST)
                .body(UnifyResult.fail(SearchErrorCode.SEARCH_BAD_REQUEST, safeMessage(ex)));
    }

    private ResponseEntity<UnifyResult<Void>> unavailable(Exception ex) {
        log.warn("搜索不可用 trace={} type={}", MDC.get(TraceConstants.MDC_KEY),
                ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SC_SERVICE_UNAVAILABLE)
                .body(UnifyResult.fail(SearchErrorCode.SEARCH_UNAVAILABLE));
    }

    /** 遍历 cause 链：连接拒绝/读超时/404（索引或别名不存在）视为搜索不可用。 */
    private boolean isSearchUnavailable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof ResponseException re
                    && re.getResponse() != null
                    && re.getResponse().getStatusLine() != null
                    && re.getResponse().getStatusLine().getStatusCode() == 404) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    private String safeMessage(IllegalArgumentException ex) {
        String message = ex.getMessage();
        // 仅回显参数语义文案，禁止 ES 主机/索引内部细节
        return message == null || message.isBlank()
                ? SearchErrorCode.SEARCH_BAD_REQUEST.getMessage() : message;
    }
}
