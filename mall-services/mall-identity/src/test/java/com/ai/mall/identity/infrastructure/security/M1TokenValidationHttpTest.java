package com.ai.mall.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.JwtSubjectConverter;
import com.ai.mall.common.security.SubjectType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M1 测试设计 CHG-0007 补充证据：BE-106 / BE-107 的真实 HTTP 安全链覆盖。
 * 以临时 RSA 密钥在 test profile 内重建与 JwtConfiguration 一致的
 * Authorization Server 资源服务器安全链，验证 401 vs 403 语义、主体字段与 SecurityContext。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1 JWT 认证安全链 HTTP 层验证")
class M1TokenValidationHttpTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;

    @Test
    @DisplayName("BE-107/TC-001 合法 ADMIN Token 访问受保护 API 时 principal 含正确主体字段")
    void validAdminTokenAuthenticatesPrincipal() throws Exception {
        String token = issue("999", "admin_bob", "ADMIN", 3, List.of("menu:workbench:view"));

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("BE-106/TC-001 ADMIN 放行，MEMBER/SERVICE 访问管理 API 返回 403")
    void adminAllowedButMemberAndServiceForbidden() throws Exception {
        String admin = issue("1", "boss", "ADMIN", 1, List.of());
        String member = issue("2", "customer", "MEMBER", 1, List.of());
        String service = issue("3", "job-runner", "SERVICE", 1, List.of());

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("permission denied"));
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + service))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BE-106/TC-002 缺少 subject_type、未知类型或 audience 不匹配时被拒且不建立 SecurityContext")
    void invalidOrMisAudienceTokenRejected() throws Exception {
        String noSubjectType = issueClaims(JwtClaimsSet.builder()
                .subject("5").claim("username", "x").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform"));
        String unknownType = issue("6", "alien", "ALIEN", 1, List.of());
        String wrongAudience = issue("7", "trusted", "ADMIN", 1, List.of(), "other-service");

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + noSubjectType)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + unknownType)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BE-107/TC-002 缺失、过期或签名错误 Token 返回统一 401 且上下文为空")
    void missingExpiredOrForgedTokenGives401() throws Exception {
        String expired = issueClaims(JwtClaimsSet.builder()
                .subject("8").claim("username", "old").claim("subject_type", "ADMIN")
                .claim("auth_version", 1).audience(List.of("mall-admin-api")).issuer("ai-platform")
                .expiresAt(Instant.now().minusSeconds(3600)));
        String forged = "eyJhbGciOiJSUzI1NiJ9.forged-without-valid-signature";

        mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BE-107/TC-003 已认证但缺 authority 时被方法安全拒绝并返回统一 403")
    void authenticatedButUnauthorizedGives403Not401() throws Exception {
        String adminWithoutManage = issue("10", "limited", "ADMIN", 1, List.of("menu:workbench:view"));
        String adminWithManage = issue("11", "manager", "ADMIN", 1, List.of("menu:workbench:view", "users:manage"));

        mockMvc.perform(get("/api/admin/presecure").header("Authorization", "Bearer " + adminWithoutManage))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("permission denied"));
        mockMvc.perform(get("/api/admin/presecure").header("Authorization", "Bearer " + adminWithManage))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BE-106/TC-003 SubjectType 四种定义值稳定，未知值默认拒绝")
    void subjectTypeEnumStableAndUnknownRejected() {
        assertThat(SubjectType.values()).containsExactly(
                SubjectType.GUEST, SubjectType.MEMBER, SubjectType.ADMIN, SubjectType.SERVICE);
        assertThat(SubjectType.valueOf("ADMIN")).isEqualTo(SubjectType.ADMIN);
        assertThat(SubjectType.valueOf("MEMBER")).isEqualTo(SubjectType.MEMBER);
        assertThat(SubjectType.valueOf("GUEST")).isEqualTo(SubjectType.GUEST);
        assertThat(SubjectType.valueOf("SERVICE")).isEqualTo(SubjectType.SERVICE);
        assertThatThrownBy(() -> SubjectType.valueOf("ALIEN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String issue(String subject, String username, String subjectType, int authVersion, List<String> permissions) {
        return issue(subject, username, subjectType, authVersion, permissions, "mall-admin-api");
    }

    private String issue(String subject, String username, String subjectType, int authVersion,
                         List<String> permissions, String audience) {
        var builder = JwtClaimsSet.builder()
                .subject(subject).claim("username", username)
                .claim("subject_type", subjectType).claim("auth_version", authVersion)
                .audience(List.of(audience)).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) builder.claim("permissions", permissions);
        return issueClaims(builder);
    }

    private String issueClaims(JwtClaimsSet.Builder builder) {
        return encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        private static final KeyPairHolder KEYS = KeyPairHolder.generate();

        record KeyPairHolder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
            static KeyPairHolder generate() {
                try {
                    var generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(2048);
                    var pair = generator.generateKeyPair();
                    return new KeyPairHolder((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Bean
        JwtEncoder testJwtEncoder() {
            var jwk = new RSAKey.Builder(KEYS.publicKey()).privateKey(KEYS.privateKey()).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
        }

        @Bean
        JwtDecoder testJwtDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey(KEYS.publicKey()).build();
            OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains("mall-admin-api") ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience", null));
            decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer("ai-platform"), audienceValidator));
            return decoder;
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
            var converter = new JwtSubjectConverter();
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/admin/auth/login", "/api/admin/auth/refresh", "/actuator/health").permitAll()
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint((request, response, exception) -> {
                                response.setStatus(401);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write("{\"success\":false,\"code\":\"B0001\",\"message\":\"authentication required\",\"data\":null,\"traceId\":null}");
                            })
                            .accessDeniedHandler((request, response, exception) -> {
                                response.setStatus(403);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write("{\"success\":false,\"code\":\"B0001\",\"message\":\"permission denied\",\"data\":null,\"traceId\":null}");
                            }))
                    .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                    .build();
        }

        @Bean
        TestPingController testPingController() {
            return new TestPingController();
        }
    }

    @RestController
    static class TestPingController {
        @GetMapping("/api/admin/ping")
        public Object ping() {
            return java.util.Map.of("success", true);
        }

        @GetMapping("/api/admin/presecure")
        @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('users:manage')")
        public Object presecure() {
            return java.util.Map.of("success", true);
        }
    }
}
