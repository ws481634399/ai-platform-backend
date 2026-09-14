package com.ai.mall.gateway.security;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!test")
public class GatewaySecurityConfiguration {
    /** CHG-0015：内部端点仅允许服务间直连服务端口，经网关一律按"不存在"处理，不暴露端点存在性。 */
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        var authenticationConverter = new ReactiveJwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> "ADMIN".equals(jwt.getClaimAsString("subject_type"))
                ? Flux.just(new SimpleGrantedAuthority("ROLE_ADMIN")) : Flux.empty());
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // CHG-0015 显式白名单（本 Change 最小集；商城其余公开路由在后续 Change 扩展）
                        .pathMatchers("/api/admin/auth/login", "/api/admin/auth/refresh",
                                "/api/mall/products/**", "/actuator/health").permitAll()
                        // CHG-0015：内部端点经网关全部拒绝（匿名 → 404、持任意身份 → 404，见异常处理）
                        .pathMatchers("/api/internal/**").denyAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .exceptionHandling(errors -> errors
                        // 匿名访问被拒：内部端点也必须返回 404（ExceptionTranslation 对匿名走 entryPoint）
                        .authenticationEntryPoint((exchange, ex) -> isInternal(exchange)
                                ? writeError(exchange, HttpStatus.NOT_FOUND, "not found")
                                : writeError(exchange, HttpStatus.UNAUTHORIZED, "authentication required"))
                        // 已认证但无权：denyAll 命中内部端点时返回 404，其余 403
                        .accessDeniedHandler((exchange, ex) -> isInternal(exchange)
                                ? writeError(exchange, HttpStatus.NOT_FOUND, "not found")
                                : writeError(exchange, HttpStatus.FORBIDDEN, "permission denied")))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
                .build();
    }

    private static boolean isInternal(org.springframework.web.server.ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${mall.security.jwt.public-key-location}") Resource publicResource,
                                  @Value("${mall.security.jwt.issuer:ai-platform}") String issuer,
                                  @Value("${mall.security.jwt.audience:mall-admin-api}") String audience) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(publicResource.getInputStream());
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        var audienceValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt>) jwt ->
                jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    private static reactor.core.publisher.Mono<Void> writeError(org.springframework.web.server.ServerWebExchange exchange,
                                                                 HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"success\":false,\"code\":\"B0001\",\"message\":\"" + message
                + "\",\"data\":null,\"traceId\":null}";
        return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(
                exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))));
    }
}
