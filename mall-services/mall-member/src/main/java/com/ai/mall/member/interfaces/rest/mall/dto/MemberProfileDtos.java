package com.ai.mall.member.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.Size;

/**
 * 会员资料接口 DTO（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>契约 SSOT：story-design §2。memberId 永不出现在请求体；出参 ID 一律字符串。
 */
public final class MemberProfileDtos {

    private MemberProfileDtos() {
    }

    /** 资料视图：{@code GET/PUT /api/mall/members/me}。 */
    public record ProfileView(
            @StringId long memberId,
            String username,
            String nickname,
            String avatarUrl,
            String gender,
            String phone,
            String email) {
    }

    /**
     * 修改资料请求：部分更新——字段为 null 表示保留原值；phone/email 空串表示清空
     * （清空/合并语义在应用层归一，故空串不做正则校验）。
     */
    public record UpdateProfileRequest(
            @Size(min = 1, max = 32, message = "昵称长度必须在1-32字之间") String nickname,
            String gender,
            @Size(max = 20, message = "手机号长度不能超过20位") String phone,
            @Size(max = 128, message = "邮箱长度不能超过128位") String email) {
    }

    /** 头像上传响应。 */
    public record AvatarUploadResponse(String avatarUrl) {
    }
}
