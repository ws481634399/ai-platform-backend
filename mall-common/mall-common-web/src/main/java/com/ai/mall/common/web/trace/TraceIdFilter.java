package com.ai.mall.common.web.trace;

import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.common.core.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * TraceId 过滤器：缺省生成、合法透传、写入 MDC 与 TraceContext、响应头回写。
 *
 * <p>合法 TraceId 定义为 32 位十六进制小写（与 TraceContext.generate 输出一致）；
 * 请求结束后必须清理上下文，防止线程池复用导致串号。注册顺序为最高优先级，
 * 保证后续任何组件打日志时 traceId 已进入 MDC。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceConstants.TRACE_HEADER);
        if (traceId == null || !VALID_TRACE_ID.matcher(traceId).matches()) {
            traceId = TraceContext.generate();
        }
        TraceContext.set(traceId);
        MDC.put(TraceConstants.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // finally 中回写与清理：异常路径同样保证响应头存在且上下文无残留
            response.setHeader(TraceConstants.TRACE_HEADER, traceId);
            MDC.remove(TraceConstants.MDC_KEY);
            TraceContext.clear();
        }
    }
}
