package com.ai.mall.common.core.trace;

import java.util.UUID;

/**
 * TraceId 上下文（纯 Java 实现，零第三方依赖，守 AC-12 轻量性）。
 *
 * <p>以 ThreadLocal 承载当前线程的 TraceId；Servlet 侧的写入/清理由
 * mall-common-web 的 TraceIdFilter 负责，Feign/MQ 等跨进程传播场景后续按需扩展。
 * 必须注意线程池场景下的清理（finally 中 clear），避免串号。
 */
public final class TraceContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    /** 生成 TraceId：UUID 去横线，32 位十六进制小写 */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 写入当前线程上下文 */
    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    /** 读取当前线程上下文，未设置时返回 null */
    public static String get() {
        return HOLDER.get();
    }

    /** 清理当前线程上下文（请求结束必须调用，防线程池复用污染） */
    public static void clear() {
        HOLDER.remove();
    }
}
