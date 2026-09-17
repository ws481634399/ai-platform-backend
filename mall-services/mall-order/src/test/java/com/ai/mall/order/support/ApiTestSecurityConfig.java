package com.ai.mall.order.support;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.security.JwtSubjectConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * mall-order 测试安全链（CHG-0019）：进程内临时 RSA 密钥；
 * 权限码直接取 JWT permissions claim（不查 Redis 授权快照），便于按用例授予 order:* 权限；
 * /api/internal/** 接入 InternalIdentityFilter 仅认 X-Internal-Token（与生产一致）。
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiTestSecurityConfig {

    public static final String ISSUER = "ai-platform";
    public static final String AUDIENCE = "mall-admin-api";

    private static final Keys KEYS = Keys.generate();

    private record Keys(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        static Keys generate() {
            try {
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                var pair = generator.generateKeyPair();
                return new Keys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
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
        var audienceValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<
                org.springframework.security.oauth2.jwt.Jwt>) jwt ->
                jwt.getAudience().contains(AUDIENCE) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http, JwtDecoder decoder, ObjectMapper objectMapper,
                                                InternalIdentityFilter internalIdentityFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("SERVICE")
                        .requestMatchers("/api/mall/**").hasRole("MEMBER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(internalIdentityFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "authentication required"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, "permission denied"));
                        }))
                .oauth2ResourceServer(resource -> resource.jwt(jwt ->
                        jwt.decoder(decoder).jwtAuthenticationConverter(new JwtSubjectConverter())))
                .build();
    }
}
