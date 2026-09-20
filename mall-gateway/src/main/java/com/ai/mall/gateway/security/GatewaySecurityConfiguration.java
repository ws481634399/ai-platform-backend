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
        // CHG-0016：subject_type claim → ROLE_<TYPE>（ADMIN/MEMBER 双向隔离）；未知/缺失不给角色
        var authenticationConverter = new ReactiveJwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String subjectType = jwt.getClaimAsString("subject_type");
            return "ADMIN".equals(subjectType) || "MEMBER".equals(subjectType)
                    ? Flux.just(new SimpleGrantedAuthority("ROLE_" + subjectType)) : Flux.empty();
        });
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // 显式白名单：管理端登录刷新（CHG-0015）+ 会员注册登录刷新（CHG-0016）+ 公开商品浏览
                        .pathMatchers("/api/admin/auth/login", "/api/admin/auth/refresh",
                                "/api/auth/member/register", "/api/auth/member/login",
                                "/api/auth/member/refresh",
                                "/api/mall/products/**", "/api/mall/categories/**",
                                "/api/mall/brands/**", "/api/mall/home", "/api/mall/skus/**",
                                // CHG-0020：商品搜索匿名可访问
                                "/api/mall/search/**",
                                // CHG-0022：公开功能开关匿名可访问
                                "/api/mall/public-features/**",
                                // CHG-0024：AI 开放端点匿名放行（GUEST 可用，功能开关在 ai-service 内 fail-closed）
                                "/api/ai/shopping/**", "/api/ai/compare/**", "/api/ai/support/**",
                                "/actuator/health").permitAll()
                        // CHG-0015：内部端点经网关全部拒绝（匿名 → 404、持任意身份 → 404，见异常处理）
                        .pathMatchers("/api/internal/**").denyAll()
                        // CHG-0016：会员域仅 MEMBER（与 /api/admin/** 互不重叠，双向 403）
                        // CHG-0018 DU-BE-801：会员购物车同属 MEMBER（游客车与合并在 Story3 另议）
                        .pathMatchers("/api/mall/members/**", "/api/mall/shipping-addresses/**",
                                "/api/mall/cart/**", "/api/mall/orders/**").hasRole("MEMBER")
                        // CHG-0024：AI 会员端点仅 MEMBER（订单助手等），管理端点仅 ADMIN（知识库管理）
                        .pathMatchers("/api/ai/members/**").hasRole("MEMBER")
                        .pathMatchers("/api/ai/admin/**").hasRole("ADMIN")
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
