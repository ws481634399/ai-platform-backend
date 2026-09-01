package com.ai.mall.common.core.trace;

/**
 * TraceId 相关常量。
 *
 * <p>header 名与 MDC key 为平台级公共约定（design.md §2.3 接口契约），
 * 供 Web 层（TraceIdFilter）与日志配置共同引用，字段值一经发布不得随意变更。
 */
public final class TraceConstants {

    /** TraceId 在 HTTP 请求/响应头中的名称 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** TraceId 在日志 MDC 中的 key（logback pattern 以 %X{traceId} 引用） */
    public static final String MDC_KEY = "traceId";

    private TraceConstants() {
    }
}
