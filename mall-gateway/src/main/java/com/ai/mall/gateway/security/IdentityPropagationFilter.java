package com.ai.mall.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityPropagationFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate().headers(headers ->
                headers.keySet().removeIf(name -> {
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    return lower.startsWith("x-subject-") || lower.startsWith("x-auth-")
                            || lower.startsWith("x-internal-identity-");
                })).build();
        ServerWebExchange safeExchange = exchange.mutate().request(stripped).build();
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getPrincipal())
                .filter(Jwt.class::isInstance).cast(Jwt.class)
                .flatMap(jwt -> {
                    ServerHttpRequest trusted = stripped.mutate()
                            .header("X-Subject-Id", jwt.getSubject())
                            .header("X-Subject-Type", jwt.getClaimAsString("subject_type"))
                            .header("X-Subject-Name", jwt.getClaimAsString("username"))
                            .header("X-Auth-Version", String.valueOf((Object) jwt.getClaim("auth_version"))).build();
                    return chain.filter(exchange.mutate().request(trusted).build());
                }).switchIfEmpty(chain.filter(safeExchange));
    }

    @Override public int getOrder() { return -1; }
}
