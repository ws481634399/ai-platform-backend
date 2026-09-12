package com.ai.mall.identity.config;

import com.ai.mall.identity.token.AccessTokenService;
import com.ai.mall.identity.token.RefreshTokenService;
import com.ai.mall.identity.token.TokenPairService;
import com.ai.mall.identity.auth.AdminCredentialRepository;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.ai.mall.common.security.JwtSubjectConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

@Configuration
@Profile("!test")
@EnableMethodSecurity
public class JwtConfiguration {
    @Bean
    JwtEncoder jwtEncoder(@Value("${mall.security.jwt.public-key-location}") Resource publicResource,
                          @Value("${mall.security.jwt.private-key-location}") Resource privateResource) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(publicResource.getInputStream());
        RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateResource.getInputStream());
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key));
        return new NimbusJwtEncoder(source);
    }

    @Bean JwtDecoder jwtDecoder(@Value("${mall.security.jwt.public-key-location}") Resource publicResource,
                                @Value("${mall.security.jwt.issuer:ai-platform}") String issuer,
                                @Value("${mall.security.jwt.audience:mall-admin-api}") String audience) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(publicResource.getInputStream());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience", null));
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, ObjectMapper objectMapper) throws Exception {
        var converter = new JwtSubjectConverter();
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/refresh", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper, 401, "authentication required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper, 403, "permission denied")))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR, message));
    }

    @Bean
    AccessTokenService accessTokenService(JwtEncoder encoder,
            @Value("${mall.security.jwt.issuer:ai-platform}") String issuer,
            @Value("${mall.security.jwt.audience:mall-admin-api}") String audience,
            @Value("${mall.security.jwt.access-ttl:PT15M}") Duration ttl) {
        return new AccessTokenService(encoder, Clock.systemUTC(), issuer, audience, ttl);
    }

    @Bean TokenPairService tokenPairService(AccessTokenService access, RefreshTokenService refresh,
                                            AdminCredentialRepository admins) {
        return new TokenPairService(access, refresh, admins);
    }
}
