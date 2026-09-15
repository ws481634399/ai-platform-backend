package com.ai.mall.gateway.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * CHG-0016 DU-BE-602 网关安全链切片测试：
 * 真实加载 {@link GatewaySecurityConfiguration}（含本次 MEMBER 角色扩展），下游 200 桩。
 *
 * <ul>
 *   <li>TC-007 会员注册/登录/刷新匿名放行；/api/mall/products 公开例外保持</li>
 *   <li>TC-008 会员域仅 MEMBER：匿名 401、MEMBER 200、ADMIN 403；MEMBER 访问管理端 403（双向隔离）</li>
 * </ul>
 */
@SpringJUnitConfig(CHG0016GatewaySecurityChainTest.ChainConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CHG-0016 网关安全链：会员白名单与 MEMBER/ADMIN 双向隔离")
class CHG0016GatewaySecurityChainTest {

    private static final KeyMaterial KEYS = KeyMaterial.generate();

    @EnableWebFluxSecurity
    @Import(GatewaySecurityConfiguration.class)
    static class ChainConfig {
    }

    private final SecurityWebFilterChain chain;

    CHG0016GatewaySecurityChainTest(@Autowired SecurityWebFilterChain chain) {
        this.chain = chain;
    }

    private NimbusJwtEncoder encoder;
    private WebTestClient client;

    @BeforeAll
    void initEncoder() {
        var jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.keyPair().getPublic())
                .privateKey(KEYS.keyPair().getPrivate()).build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
    }

    @BeforeEach
    void setUp() {
        WebFilterChainProxy proxy = new WebFilterChainProxy(chain);
        client = WebTestClient.bindToWebHandler(exchange -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    return exchange.getResponse().setComplete();
                })
                .webFilter(proxy)
                .build();
    }

    @AfterAll
    void tearDown() {
        try {
            Files.deleteIfExists(KEYS.publicKeyPem());
        } catch (IOException ignored) {
            KEYS.publicKeyPem().toFile().deleteOnExit();
        }
    }

    @DynamicPropertySource
    static void jwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("mall.security.jwt.public-key-location", () -> "file:" + KEYS.publicKeyPem());
    }

    @Test
    @DisplayName("TC-007a 匿名 POST 会员 register/login/refresh → 放行（200）")
    void memberAuthEndpointsWhitelisted() {
        client.post().uri("/api/auth/member/register").exchange().expectStatus().isOk();
        client.post().uri("/api/auth/member/login").exchange().expectStatus().isOk();
        client.post().uri("/api/auth/member/refresh").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-007b 公开商品浏览例外保持匿名可访问")
    void mallProductsStillPublic() {
        client.get().uri("/api/mall/products/42").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-008a 匿名访问会员域（members/shipping-addresses）→ 401")
    void memberScopeRequiresAuthentication() {
        client.get().uri("/api/mall/members/me").exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("B0001");
        client.get().uri("/api/mall/shipping-addresses").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("TC-008b MEMBER Token 访问会员域 → 200")
    void memberTokenAcceptedForMemberScope() {
        client.get().uri("/api/mall/members/me")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange().expectStatus().isOk();
        client.get().uri("/api/mall/shipping-addresses")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-008c ADMIN Token 访问会员域 → 403")
    void adminTokenForbiddenForMemberScope() {
        client.get().uri("/api/mall/members/me")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("B0001");
    }

    @Test
    @DisplayName("TC-008d MEMBER Token 访问管理端 → 403（双向隔离，与 CHG-0015 矩阵一致）")
    void memberTokenForbiddenForAdminScope() {
        client.post().uri("/api/admin/auth/logout")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange().expectStatus().isForbidden();
    }

    private String token(String subjectType) {
        var claims = JwtClaimsSet.builder()
                .subject("2001").claim("username", "member_tester")
                .claim("subject_type", subjectType)
                .claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** 测试密钥材料：RSA 2048 + X.509 公钥 PEM 临时文件。 */
    private record KeyMaterial(KeyPair keyPair, Path publicKeyPem) {
        static KeyMaterial generate() {
            try {
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(pair.getPublic().getEncoded());
                String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
                Path file = Files.createTempFile("gw16-test-public-", ".pem");
                Files.writeString(file, pem, StandardCharsets.UTF_8);
                return new KeyMaterial(pair, file);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
