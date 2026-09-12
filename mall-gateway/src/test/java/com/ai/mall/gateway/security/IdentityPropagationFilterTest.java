package com.ai.mall.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdentityPropagationFilterTest {
    @Test void stripsSpoofedIdentityHeadersAndWritesVerifiedClaims() {
        var request = MockServerHttpRequest.get("/api/admin/users")
                .header("Authorization", "Bearer signed")
                .header("X-Subject-Id", "spoof")
                .header("X-Auth-Evil", "spoof")
                .header("X-Internal-Identity-Test", "spoof").build();
        var captured = new AtomicReference<ServerWebExchange>();
        var jwt = new Jwt("signed", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("sub", "42", "subject_type", "ADMIN", "username", "alice", "auth_version", 7));
        var authentication = new UsernamePasswordAuthenticationToken(jwt, null);

        new IdentityPropagationFilter().filter(MockServerWebExchange.from(request), exchange -> {
            captured.set(exchange); return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();

        var headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer signed");
        assertThat(headers.getFirst("X-Subject-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-Subject-Type")).isEqualTo("ADMIN");
        assertThat(headers.getFirst("X-Subject-Name")).isEqualTo("alice");
        assertThat(headers.getFirst("X-Auth-Version")).isEqualTo("7");
        assertThat(headers.containsKey("X-Auth-Evil")).isFalse();
        assertThat(headers.containsKey("X-Internal-Identity-Test")).isFalse();
    }
}
