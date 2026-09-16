package com.ai.mall.cart.infrastructure.config;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.security.JwtSubjectConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * mall-cart 资源服务器安全链（CHG-0018 DU-BE-801）。
 *
 * <p>JWT 仅解码验证（与 identity 共用验签公钥），主体角色由 {@link JwtSubjectConverter}
 * 从 subject_type claim 映射；{@code /api/mall/cart/**} 路径层 MEMBER 收口，
 * 方法级 {@code @PreAuthorize} 纵深防御（ADMIN 越界 403，无 Token 401）。
 * {@code /api/internal/**} 仅接受 X-Internal-Token（ROLE_SERVICE），任何 JWT 不放行。
 * test profile 由 ApiTestSecurityConfig 提供等价链路。
 */
@Configuration
@Profile("!test")
@EnableMethodSecurity
public class CartSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(@Value("${mall.security.jwt.public-key-location}") Resource publicResource,
                          @Value("${mall.security.jwt.issuer:ai-platform}") String issuer,
                          @Value("${mall.security.jwt.audience:mall-admin-api}") String audience) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(publicResource.getInputStream());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        var audienceValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<
                org.springframework.security.oauth2.jwt.Jwt>) jwt ->
                jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, ObjectMapper objectMapper,
                                            ObjectProvider<InternalIdentityFilter> internalFilterProvider)
            throws Exception {
        InternalIdentityFilter internalFilter = internalFilterProvider.getIfAvailable();
        var chain = http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("SERVICE")
                        .requestMatchers("/api/mall/**").hasRole("MEMBER")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, 401, "authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, 403, "permission denied")))
                .oauth2ResourceServer(resource ->
                        resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(new JwtSubjectConverter())));
        if (internalFilter != null) {
            chain.addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return chain.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, message));
    }
}
