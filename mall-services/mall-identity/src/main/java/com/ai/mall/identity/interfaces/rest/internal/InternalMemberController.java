package com.ai.mall.identity.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.annotation.StringId;
import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * identity 侧会员内部接口（CHG-0016）：仅供 mall-member 经 X-Internal-Token 调用。
 *
 * <p>profile-seed 懒补偿种子：mall-member 发现会员档案缺失时反向取回 identity 侧
 * 账号种子（memberId/username/status）幂等补建；账号不存在返回 404。
 * 契约 SSOT：requirement-design §4。
 */
@RestController
@RequestMapping("/api/internal/members")
public class InternalMemberController {

    private final MemberUserRepository members;

    public InternalMemberController(MemberUserRepository members) {
        this.members = members;
    }

    @GetMapping("/{memberId}/profile-seed")
    public UnifyResult<ProfileSeedResponse> profileSeed(@PathVariable long memberId) {
        MemberAccount account = members.findById(memberId)
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "会员不存在"));
        return UnifyResult.ok(new ProfileSeedResponse(
                account.id(), account.username().value(), account.status().name()));
    }

    /** 档案种子视图：memberId 字符串出参。 */
    public record ProfileSeedResponse(@StringId long memberId, String username, String status) {
    }
}
