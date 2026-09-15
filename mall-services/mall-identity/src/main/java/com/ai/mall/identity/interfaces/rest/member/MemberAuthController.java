package com.ai.mall.identity.interfaces.rest.member;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.annotation.StringId;
import com.ai.mall.identity.application.member.MemberRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城会员认证接口（CHG-0016）。
 *
 * <p>本 Story 仅注册端点：{@code POST /api/auth/member/register}（匿名，网关白名单下条 Story 配置）。
 * 响应只含 memberId（字符串出参，{@link StringId}），不含密码、不含 Token（注册成功引导登录）。
 * 字段级 400 由全局/Identity 异常处理承载；用户名冲突 409 由应用服务 UseCaseException 承载。
 */
@RestController
@RequestMapping("/api/auth/member")
public class MemberAuthController {

    private final MemberRegistrationService registration;

    public MemberAuthController(MemberRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    public ResponseEntity<UnifyResult<RegisterMemberResponse>> register(
            @Valid @RequestBody RegisterMemberRequest request) {
        long memberId = registration.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UnifyResult.ok(new RegisterMemberResponse(memberId)));
    }

    /** 注册请求：结构规则（4-20 等）在领域层细化校验，此处仅保证非空白字段级提示。 */
    public record RegisterMemberRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 注册响应：memberId 字符串序列化，规避 JS Number 精度问题。 */
    public record RegisterMemberResponse(@StringId long memberId) {
    }
}
