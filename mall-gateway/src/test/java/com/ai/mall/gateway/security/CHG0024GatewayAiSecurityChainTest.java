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
 * CHG-0024 DU-BE-001 网关安全链切片测试：
 * 真实加载 {@link GatewaySecurityConfiguration}（含本次 /api/ai/** 角色矩阵扩展），下游 200 桩。
 *
 * <ul>
 *   <li>TC 矩阵一：/api/ai/shopping|compare|support 匿名放行（GUEST 可用，开关在 ai-service 内 fail-closed）</li>
 *   <li>TC 矩阵二：/api/ai/members/** 仅 MEMBER（ADMIN 403）；/api/ai/admin/** 仅 ADMIN（MEMBER 403）</li>
 *   <li>既有矩阵回归：/api/internal/** 仍 404、公开商品仍放行</li>
 * </ul>
 */
@SpringJUnitConfig(CHG0024GatewayAiSecurityChainTest.ChainConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CHG-0024 网关安全链：/api/ai/** 角色矩阵（GUEST 开放/MEMBER/ADMIN）")
class CHG0024GatewayAiSecurityChainTest {

    private static final KeyMaterial KEYS = KeyMaterial.generate();

    @EnableWebFluxSecurity
    @Import(GatewaySecurityConfiguration.class)
    static class ChainConfig {
    }

    private final SecurityWebFilterChain chain;

    CHG0024GatewayAiSecurityChainTest(@Autowired SecurityWebFilterChain chain) {
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
    @DisplayName("匿名访问 AI 开放端点（shopping/compare/support）→ 放行（200）")
    void aiOpenEndpointsWhitelisted() {
        client.post().uri("/api/ai/shopping/recommendations").exchange().expectStatus().isOk();
        client.post().uri("/api/ai/compare").exchange().expectStatus().isOk();
        client.post().uri("/api/ai/support/chat").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("匿名访问 AI 会员端点（orders assistant）→ 401")
    void aiMemberScopeRequiresAuthentication() {
        client.post().uri("/api/ai/members/orders/assistant").exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("B0001");
    }

    @Test
    @DisplayName("MEMBER Token 访问 AI 会员端点 → 200")
    void memberTokenAcceptedForAiMemberScope() {
        client.post().uri("/api/ai/members/orders/assistant")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("ADMIN Token 访问 AI 会员端点 → 403；MEMBER 访问 AI 管理端点 → 403")
    void aiMemberAndAdminScopesAreMutuallyExclusive() {
        client.post().uri("/api/ai/members/orders/assistant")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/ai/admin/knowledge/documents")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("ADMIN Token 访问 AI 管理端点（知识库管理）→ 200")
    void adminTokenAcceptedForAiAdminScope() {
        client.get().uri("/api/ai/admin/knowledge/documents")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/ai/admin/knowledge/documents")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("既有矩阵回归：/api/internal/** 仍 404、公开商品仍放行")
    void existingMatrixUnchanged() {
        client.get().uri("/api/internal/products/1").exchange().expectStatus().isNotFound();
        client.get().uri("/api/mall/products/42").exchange().expectStatus().isOk();
    }

    private String token(String subjectType) {
        var claims = JwtClaimsSet.builder()
                .subject("2001").claim("username", "ai_tester")
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
                Path file = Files.createTempFile("gw24-test-public-", ".pem");
                Files.writeString(file, pem, StandardCharsets.UTF_8);
                return new KeyMaterial(pair, file);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
