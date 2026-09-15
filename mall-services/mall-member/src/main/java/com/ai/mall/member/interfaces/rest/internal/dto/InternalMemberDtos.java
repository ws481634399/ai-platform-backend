package com.ai.mall.member.interfaces.rest.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

/**
 * 会员内部接口 DTO（CHG-0016）：与 identity MemberProvisionClient 的事件契约对齐。
 */
public final class InternalMemberDtos {

    private InternalMemberDtos() {
    }

    /**
     * provision 请求：MemberRegistered v1 事件载荷。
     * memberId 线上为字符串（@StringId 出参侧约定），Jackson 可直接反序列化进 long。
     */
    public record ProvisionRequest(
            @NotBlank(message = "eventId不能为空") String eventId,
            @NotNull(message = "memberId不能为空") @Positive(message = "memberId必须为正数") Long memberId,
            @NotBlank(message = "username不能为空") String username,
            String nickname,
            @NotNull(message = "occurredAt不能为空") Instant occurredAt) {
    }

    /**
     * provision 响应：provisioned=false 表示事件重放/补偿重复（投递本身成功）。
     */
    public record ProvisionResponse(boolean provisioned) {
    }
}
