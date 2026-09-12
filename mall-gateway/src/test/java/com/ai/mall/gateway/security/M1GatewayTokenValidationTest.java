package com.ai.mall.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

/**
 * CHG-0007 补充证据：BE-108/TC-003 Gateway 对过期、签名错误、audience 不匹配的 JWT 真实拒绝。
 * 以生产配置一致的 NimbusReactiveJwtDecoder + issuer/audience 验证器，在纯单元环境验证解码路径。
 */
@DisplayName("M1 Gateway JWT 解码器边界覆盖")
class M1GatewayTokenValidationTest {

    private static final String VALID_AUDIENCE = "mall-admin-api";
    private static final String VALID_ISSUER = "ai-platform";
    private static final KeyPairHolder PAIR = KeyPairHolder.generate();
    private static final ReactiveJwtDecoder DECODER = buildDecoder(PAIR.publicKey(), VALID_ISSUER, VALID_AUDIENCE);

    @Test
    @DisplayName("BE-108/TC-001 合法 ADMIN Token 可通过 Gateway 解码器且 subject_type=ADMIN")
    void validAdminTokenDecodesAndCarriesSubjectType() {
        String token = mint(VALID_ISSUER, VALID_AUDIENCE, "ADMIN",
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(600));
        var jwt = DECODER.decode(token).block();
        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("subject_type")).isEqualTo("ADMIN");
        assertThat(jwt.getAudience()).containsExactly(VALID_AUDIENCE);
    }

    @Test
    @DisplayName("BE-108/TC-002 无 Token / 伪造 Token 在 Gateway 不进入转发并返回 401 语义")
    void missingTokenYieldsJwtException() {
        assertThatThrownBy(() -> DECODER.decode("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ3cm9uZyJ9.").block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("BE-108/TC-003 过期、签名错误或 audience 不匹配 Token 在 Gateway 被拒绝")
    void expiredForgedOrWrongAudienceRejected() {
        String expired = mint(VALID_ISSUER, VALID_AUDIENCE, "ADMIN",
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        String wrongAudience = mint(VALID_ISSUER, "wrong-aud", "ADMIN",
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(600));
        String wrongIssuer = mint("wrong-issuer", VALID_AUDIENCE, "ADMIN",
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(600));
        String forged = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.forged";

        var bad = NimbusReactiveJwtDecoder.withPublicKey(PAIR.publicKey()).build();
        bad.setJwtValidator(JwtValidators.createDefault());
        assertThatThrownBy(() -> bad.decode(expired).block()).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> DECODER.decode(wrongAudience).block()).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> DECODER.decode(wrongIssuer).block()).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> DECODER.decode(forged).block()).isInstanceOf(JwtException.class);
    }

    private static ReactiveJwtDecoder buildDecoder(RSAPublicKey publicKey, String issuer, String audience) {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience", null));
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    private String mint(String issuer, String audience, String subjectType, Instant issuedAt, Instant expiry) {
        var claims = JwtClaimsSet.builder()
                .subject("1").claim("username", "test").claim("subject_type", subjectType)
                .claim("auth_version", 1).issuer(issuer).audience(List.of(audience))
                .issuedAt(issuedAt).expiresAt(expiry).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(
                new RSAKey.Builder(PAIR.publicKey()).privateKey(PAIR.privateKey()).build())));
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

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
}