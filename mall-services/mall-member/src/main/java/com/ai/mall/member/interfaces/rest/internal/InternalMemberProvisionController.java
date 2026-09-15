package com.ai.mall.member.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.member.application.member.ProfileProvisionService;
import com.ai.mall.member.application.member.ProfileProvisionService.ProvisionCommand;
import com.ai.mall.member.interfaces.rest.internal.dto.InternalMemberDtos.ProvisionRequest;
import com.ai.mall.member.interfaces.rest.internal.dto.InternalMemberDtos.ProvisionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员档案内部开通接口（CHG-0016）：identity → member，X-Internal-Token 鉴权。
 *
 * <p>POST /api/internal/members/provision：消费 MemberRegistered 事件，
 * eventId/memberId 双幂等；重放返回 200 + {@code provisioned=false}（契约 requirement-design §4）。
 */
@RestController
@RequestMapping("/api/internal/members")
public class InternalMemberProvisionController {

    private final ProfileProvisionService provisionService;

    public InternalMemberProvisionController(ProfileProvisionService provisionService) {
        this.provisionService = provisionService;
    }

    @PostMapping("/provision")
    public UnifyResult<ProvisionResponse> provision(@Valid @RequestBody ProvisionRequest request) {
        boolean provisioned = provisionService.provision(new ProvisionCommand(
                request.eventId(), request.memberId(), request.username(),
                request.nickname(), request.occurredAt()));
        return UnifyResult.ok(new ProvisionResponse(provisioned));
    }
}
