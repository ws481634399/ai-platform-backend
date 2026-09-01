package com.ai.mall.common.core.result;

import com.ai.mall.common.core.trace.TraceContext;

/**
 * 统一响应结构（design.md §2.3 接口契约）。
 *
 * <p>字段冻结为五项：success/code/message/data/traceId；traceId 取自 {@link TraceContext}，
 * 由 Web 层在响应序列化前写入上下文，保证响应体与响应头/日志三者一致。
 * 纯 POJO，静态工厂统一出口，禁止业务代码手工 new。
 *
 * @param <T> 业务数据类型
 */
public class UnifyResult<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId;

    private UnifyResult() {
    }

    /** 成功响应（无数据） */
    public static UnifyResult<Void> ok() {
        return ok(null);
    }

    /** 成功响应（携带数据） */
    public static <T> UnifyResult<T> ok(T data) {
        UnifyResult<T> r = new UnifyResult<>();
        r.success = true;
        r.code = CommonErrorCode.SUCCESS.getCode();
        r.message = CommonErrorCode.SUCCESS.getMessage();
        r.data = data;
        r.traceId = TraceContext.get();
        return r;
    }

    /** 失败响应（使用错误码默认文案） */
    public static UnifyResult<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    /** 失败响应（自定义文案；禁止携带堆栈/SQL/内部细节） */
    public static <T> UnifyResult<T> fail(ErrorCode errorCode, String message) {
        UnifyResult<T> r = new UnifyResult<>();
        r.success = false;
        r.code = errorCode.getCode();
        r.message = message;
        r.traceId = TraceContext.get();
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }
}
