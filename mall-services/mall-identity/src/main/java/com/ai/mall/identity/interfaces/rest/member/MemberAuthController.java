package com.ai.mall.identity.interfaces.rest.member;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.AuthenticatedSubject;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.security.SubjectType;
import com.ai.mall.common.web.annotation.StringId;
import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.member.MemberAuthenticationApplicationService;
import com.ai.mall.identity.application.member.MemberAuthenticationApplicationService.AuthenticatedMember;
import com.ai.mall.identity.application.member.MemberTokenPairApplicationService.MemberTokenPair;
import com.ai.mall.identity.application.member.MemberRegistrationService;
import com.ai.mall.identity.application.member.MemberTokenPairApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城会员认证端点（CHG-0016）：注册（STORY-01）、登录/刷新/退出（STORY-02）。
 *
 * <p>登录与刷新同时：① 响应体返回 refreshToken（商城前端契约，见 story-design §2）；
 * ② 双发 HttpOnly + Secure + SameSite=Strict 的 refresh_token cookie（path 限定本前缀，
 * 对齐 AdminAuthController 现状）。退出清 cookie。
 */
@RestController
@RequestMapping("/api/auth/member")
public class MemberAuthController {

    /** refresh cookie 路径限定会员认证前缀，避免随其他请求发送。 */
    static final String REFRESH_COOKIE_PATH = "/api/auth/member";
    static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final MemberRegistrationService registration;
    private final MemberAuthenticationApplicationService authentication;
    private final MemberTokenPairApplicationService tokens;

    public MemberAuthController(MemberRegistrationService registration,
                                MemberAuthenticationApplicationService authentication,
                                MemberTokenPairApplicationService tokens) {
        this.registration = registration;
        this.authentication = authentication;
        this.tokens = tokens;
    }

    /** 会员注册：成功仅返回 memberId（脱敏，不回显用户名）。 */
    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public UnifyResult<RegisterMemberResponse> register(@Valid @RequestBody RegisterMemberRequest request) {
        long memberId = registration.register(request.username(), request.password());
        return UnifyResult.ok(new RegisterMemberResponse(memberId));
    }

    /** 会员登录：凭据错误 401 统一文案；禁用 403「账号已禁用」。 */
    @PostMapping("/login")
    public ResponseEntity<UnifyResult<MemberTokenResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        AuthenticatedMember member = authentication.signIn(request.username(), request.password());
        MemberTokenPair pair = tokens.issue(member);
        return tokenResponse(pair);
    }

    /** 刷新令牌：请求体 refreshToken 优先，缺省读 cookie；旋转失败/重放/版本陈旧 401。 */
    @PostMapping("/refresh")
    public ResponseEntity<UnifyResult<MemberTokenResponse>> refresh(
            @RequestBody(required = false) MemberRefreshRequest body,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken) {
        String raw = body != null && body.refreshToken() != null && !body.refreshToken().isBlank()
                ? body.refreshToken() : cookieToken;
        if (raw == null || raw.isBlank()) {
            throw new UseCaseException(UseCaseException.Kind.UNAUTHORIZED, "invalid refresh token");
        }
        return tokenResponse(tokens.refresh(raw));
    }

    /** 退出登录：撤销该会员全部 refresh 并 auth_version+1；清 refresh cookie。 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        AuthenticatedSubject subject = SecurityContextFacade.currentSubject()
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.UNAUTHORIZED, "authentication required"));
        if (subject.subjectType() != SubjectType.MEMBER) {
            throw new UseCaseException(UseCaseException.Kind.FORBIDDEN, "permission denied");
        }
        tokens.signOut(Long.parseLong(subject.subjectId()));
        ResponseCookie expired = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true).secure(true).sameSite("Strict").path(REFRESH_COOKIE_PATH).maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private ResponseEntity<UnifyResult<MemberTokenResponse>> tokenResponse(MemberTokenPair pair) {
        java.time.Duration maxAge = java.time.Duration.between(Instant.now(), pair.refreshExpiresAt());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, pair.refreshToken())
                .httpOnly(true).secure(true).sameSite("Strict").path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge.isNegative() ? java.time.Duration.ZERO : maxAge)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UnifyResult.ok(new MemberTokenResponse(pair.accessToken(), pair.accessExpiresAt(),
                        pair.refreshToken(), pair.memberId())));
    }

    /** 登录请求。 */
    public record MemberLoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 刷新请求（refreshToken 可走 cookie，故字段均可空，由控制器决定来源）。 */
    public record MemberRefreshRequest(String refreshToken) {
    }

    /** 登录/刷新双令牌响应（memberId 以字符串出参，避免 JS 长整型精度问题）。 */
    public record MemberTokenResponse(String accessToken, Instant accessExpiresAt,
                                      String refreshToken, @StringId Long memberId) {
    }

    /** 注册请求（STORY-01）：字段级规则违例由注册服务/Bean Validation 给出 400 提示。 */
    public record RegisterMemberRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 注册响应（STORY-01）：成功仅返回 memberId（字符串出参）。 */
    public record RegisterMemberResponse(@StringId Long memberId) {
    }
}
