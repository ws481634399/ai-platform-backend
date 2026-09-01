package com.ai.mall.common.core.result;

/**
 * 平台基础错误码（0/A/B/S 三段的最小集）。
 *
 * <p>仅承载跨服务通用语义；业务域错误码（如 B1xxx 按域细分）随业务需求在各自模块扩展。
 */
public enum CommonErrorCode implements ErrorCode {

    /** 成功 */
    SUCCESS("0", "成功"),

    /** 参数类通用错误（A 段） */
    PARAM_INVALID("A0001", "请求参数不合法"),

    /** 业务类通用错误（B 段） */
    BUSINESS_ERROR("B0001", "业务处理失败"),

    /** 系统类通用错误（S 段） */
    SYSTEM_ERROR("S0001", "系统繁忙，请稍后重试");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
