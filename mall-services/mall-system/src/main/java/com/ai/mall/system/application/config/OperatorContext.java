package com.ai.mall.system.application.config;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.common.security.SecurityContextFacade;

/**
 * 变更审计上下文（CHG-0022）：操作人取安全上下文 subjectId，traceId 取请求链路；
 * 无登录主体（内部/系统调用）时统一 "system"。
 */
public final class OperatorContext {

    private OperatorContext() {
    }

    public static String currentOperator() {
        return SecurityContextFacade.currentSubject()
                .map(subject -> subject.subjectId())
                .orElse("system");
    }

    public static String currentTraceId() {
        return TraceContext.get();
    }
}
