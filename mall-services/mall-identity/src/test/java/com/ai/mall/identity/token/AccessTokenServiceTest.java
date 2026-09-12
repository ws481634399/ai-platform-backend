package com.ai.mall.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;

class AccessTokenServiceTest {
    @Test void issuesRs256AdminTokenWithFrozenClaimsAndAudience() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic(); var privateKey = (RSAPrivateKey) pair.getPrivate();
        var jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
        var service = new AccessTokenService(encoder, Clock.fixed(Instant.now().plusSeconds(5), ZoneOffset.UTC),
                "ai-platform", "mall-admin-api", Duration.ofMinutes(15));
        var issued = service.issue(7, "alice", 3);
        var decoded = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(issued.value());
        assertThat(decoded.getHeaders().get("alg").toString()).isEqualTo("RS256");
        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getAudience()).containsExactly("mall-admin-api");
        assertThat(decoded.getClaimAsString("subject_type")).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("alice");
        assertThat(((Number) decoded.getClaim("auth_version")).longValue()).isEqualTo(3);
        assertThat(decoded.getId()).isNotBlank();
    }
}
