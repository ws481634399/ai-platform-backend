package com.ai.mall.identity.api;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.identity.auth.AdminAuthenticationService;
import com.ai.mall.identity.token.TokenPairService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.web.exception.BusinessException;

@RestController
@RequestMapping("/api/admin/auth")
@Profile("!test")
public class AdminAuthController {
    private final AdminAuthenticationService authentication;
    private final TokenPairService tokens;

    public AdminAuthController(AdminAuthenticationService authentication, TokenPairService tokens) {
        this.authentication = authentication;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public ResponseEntity<UnifyResult<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletResponse response) {
        var admin = authentication.authenticate(request.username(), request.password())
                .orElseThrow(() -> new InvalidCredentialsException());
        return tokenResponse(tokens.issue(admin), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<UnifyResult<TokenResponse>> refresh(
            @CookieValue(value="refresh_token", required=false) String refreshToken, HttpServletResponse response) {
        return tokenResponse(tokens.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    public UnifyResult<Void> logout(HttpServletResponse response) {
        long adminId = Long.parseLong(SecurityContextFacade.requireAdmin().subjectId());
        tokens.revokeAll(adminId);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", "").httpOnly(true)
                .secure(true).sameSite("Strict").path("/api/admin/auth").maxAge(Duration.ZERO).build().toString());
        return UnifyResult.ok();
    }

    private ResponseEntity<UnifyResult<TokenResponse>> tokenResponse(TokenPairService.TokenPair pair,
                                                                      HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", pair.refreshToken()).httpOnly(true)
                .secure(true).sameSite("Strict").path("/api/admin/auth")
                .maxAge(Duration.between(java.time.Instant.now(), pair.refreshExpiresAt()).isNegative()
                        ? Duration.ZERO : Duration.between(java.time.Instant.now(), pair.refreshExpiresAt())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(UnifyResult.ok(new TokenResponse(pair.accessToken(), pair.accessExpiresAt(), pair.refreshExpiresAt())));
    }

    public record LoginRequest(@NotBlank @Size(min=3,max=64) String username,
                               @NotBlank @Size(min=8,max=128) String password) {}
    public record TokenResponse(String accessToken, java.time.Instant accessExpiresAt,
                                java.time.Instant refreshExpiresAt) {}
    public static final class InvalidCredentialsException extends BusinessException {
        public InvalidCredentialsException() { super(CommonErrorCode.BUSINESS_ERROR, HttpStatus.UNAUTHORIZED, "invalid credentials"); }
    }
}
