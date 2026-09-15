package com.ai.mall.member.application.member;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 会员资料域错误码（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>A/B/S 段约定见 mall-common-core ErrorCode 类注释；HTTP 语义由
 * {@code BusinessException} 携带，与码段解耦（401/400/503）。
 */
public enum MemberProfileErrorCode implements ErrorCode {

    /** 档案缺失且 identity 种子不存在/拒绝（认证信息与业务数据不一致）：401，强制重新登录。 */
    PROFILE_INCONSISTENT("B0101", "会员信息异常，请重新登录"),

    /** identity 种子服务不可达（网络/5xx/信封失败）：503。 */
    PROFILE_SEED_UNAVAILABLE("S0101", "会员服务暂不可用，请稍后重试"),

    /** 上传文件非白名单图片（魔数判定，含伪装 gif）：400。 */
    FILE_TYPE_INVALID("A0101", "仅支持 jpeg/png/webp 格式的图片"),

    /** 上传文件超过 2MB：400。 */
    FILE_TOO_LARGE("A0102", "头像大小不能超过2MB"),

    /** 对象存储故障：503，不写半成品头像 URL。 */
    STORAGE_UNAVAILABLE("S0102", "头像存储暂不可用，请稍后重试");

    private final String code;
    private final String message;

    MemberProfileErrorCode(String code, String message) {
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
