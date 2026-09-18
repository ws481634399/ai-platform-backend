package com.ai.mall.common.config;

/**
 * 系统配置域错误码（CHG-0022 冻结）。
 *
 * <p>B06 段统一在 mall-common-config 定义，供所有消费服务复用，保证跨服务语义一致。
 */
public enum ConfigErrorCode implements com.ai.mall.common.core.result.ErrorCode {

    /** 参数值非法/内置配置保护类拒绝（HTTP 400）。 */
    CONFIG_VALUE_INVALID("B0601", "配置值不合法"),
    /** 配置键冲突（HTTP 409）。 */
    CONFIG_KEY_DUPLICATE("B0602", "配置键已存在"),
    /** 配置不存在（HTTP 404）。 */
    CONFIG_NOT_FOUND("B0603", "配置不存在"),
    /** 乐观锁版本冲突（HTTP 409）。 */
    CONFIG_VERSION_CONFLICT("B0604", "配置已被他人修改，请刷新后重试"),
    /** 功能开关关闭（HTTP 403）。 */
    FEATURE_DISABLED("B0606", "功能暂未开放");

    private final String code;
    private final String message;

    ConfigErrorCode(String code, String message) {
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
