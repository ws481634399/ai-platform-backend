package com.ai.mall.member.application.address;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 收货地址域错误码（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>沿用 mall-member A/B/S 码段约定（HTTP 语义由 {@code BusinessException} 携带）：
 * B02xx 为地址业务段（B01xx 已用于会员资料）。
 */
public enum AddressErrorCode implements ErrorCode {

    /** 地址不存在或不属于当前会员：统一 404，不区分两种情形以防枚举他人资源。 */
    ADDRESS_NOT_FOUND("B0201", "收货地址不存在"),

    /** 每会员地址上限 20，超出新增：409。 */
    ADDRESS_LIMIT("B0202", "收货地址最多保存20条"),

    /** 并发设置默认导致生成列唯一键冲突：409，提示重试。 */
    ADDRESS_DEFAULT_CONFLICT("B0203", "默认地址设置冲突，请重试");

    private final String code;
    private final String message;

    AddressErrorCode(String code, String message) {
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
