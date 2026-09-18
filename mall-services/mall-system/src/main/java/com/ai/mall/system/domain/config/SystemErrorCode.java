package com.ai.mall.system.domain.config;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 系统配置域错误码（CHG-0022 B06 段冻结，与 mall-common-config ConfigErrorCode 同段）。
 */
public enum SystemErrorCode implements ErrorCode {

    /** 参数值非法 / 内置配置保护类拒绝（HTTP 400）。 */
    CONFIG_VALUE_INVALID("B0601", "配置值不合法"),
    /** 配置键冲突（HTTP 409）。 */
    CONFIG_KEY_DUPLICATE("B0602", "配置键已存在"),
    /** 配置不存在（HTTP 404）。 */
    CONFIG_NOT_FOUND("B0603", "配置不存在"),
    /** 乐观锁版本冲突（HTTP 409）。 */
    CONFIG_VERSION_CONFLICT("B0604", "配置已被他人修改，请刷新后重试");

    private final String code;
    private final String message;

    SystemErrorCode(String code, String message) {
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
