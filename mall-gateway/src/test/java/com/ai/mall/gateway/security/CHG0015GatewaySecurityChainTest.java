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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * CHG-0015 DU-BE-501 网关安全链切片测试：
 * 真实加载 {@link GatewaySecurityConfiguration} 产出的 {@link SecurityWebFilterChain}，
 * 下游以 200 桩处理器模拟后端，不启动路由/Nacos。
 *
 * <ul>
 *   <li>TC-001 白名单匿名放行（/api/mall/products/**、/actuator/health）</li>
 *   <li>TC-002 /api/admin/** 无 Token 401、ADMIN Token 放行、非 ADMIN Token 403</li>
 *   <li>TC-003 /api/internal/** 经网关一律 404 同构体（匿名/持 ADMIN Token 均 404）</li>
 * </ul>
 */
@SpringJUnitConfig(CHG0015GatewaySecurityChainTest.ChainConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CHG-0015 网关安全链：白名单/鉴权/内部端点 404")
class CHG0015GatewaySecurityChainTest {

    /** 进程内 RSA 密钥对 + 临时公钥 PEM（类加载时生成，早于 Spring 上下文刷新）。 */
    private static final KeyMaterial KEYS = KeyMaterial.generate();

    /** 启用真实 GatewaySecurityConfiguration（默认 profile 下 @Profile("!test") 命中）。 */
    @EnableWebFluxSecurity
    @Import(GatewaySecurityConfiguration.class)
    static class ChainConfig {
    }

    private final SecurityWebFilterChain chain;

    CHG0015GatewaySecurityChainTest(@Autowired SecurityWebFilterChain chain) {
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
        // 下游桩：安全链放行后一律 200，等价请求成功路由到后端
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
        // Windows 下解码器可能短暂持有文件句柄，删除失败时交由 JVM 退出钩子兜底，不影响测试结论
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
    @DisplayName("TC-001a 匿名访问商城商品 → 放行（200，非 401/403）")
    void mallProductsWhitelisted() {
        client.get().uri("/api/mall/products/1").exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-001b 匿名访问健康检查 → 放行")
    void healthWhitelisted() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-002a 匿名访问管理端 → 401 同构体")
    void adminRequiresAuthentication() {
        client.get().uri("/api/admin/products").exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("B0001");
    }

    @Test
    @DisplayName("TC-002b ADMIN Token 访问管理端 → 200")
    void adminTokenAccepted() {
        client.get().uri("/api/admin/products")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("TC-002c 非 ADMIN（MEMBER）Token 访问管理端 → 403")
    void memberTokenForbidden() {
        client.get().uri("/api/admin/products")
                .header("Authorization", "Bearer " + token("MEMBER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("B0001");
    }

    @Test
    @DisplayName("TC-003a 匿名访问内部端点经网关 → 404 同构体（不暴露存在性）")
    void internalAnonymousReturns404() {
        client.get().uri("/api/internal/inventory/lock").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("B0001")
                .jsonPath("$.data").isEmpty();
    }

    @Test
    @DisplayName("TC-003b 持合法 ADMIN Token 访问内部端点经网关 → 仍 404")
    void internalWithAdminTokenStill404() {
        client.post().uri("/api/internal/inventory/lock")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("B0001");
    }

    private String token(String subjectType) {
        var claims = JwtClaimsSet.builder()
                .subject("1").claim("username", "tester")
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
                Path file = Files.createTempFile("gw-test-public-", ".pem");
                Files.writeString(file, pem, StandardCharsets.UTF_8);
                return new KeyMaterial(pair, file);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
