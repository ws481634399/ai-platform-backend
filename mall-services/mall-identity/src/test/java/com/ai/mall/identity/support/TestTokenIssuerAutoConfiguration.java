package com.ai.mall.identity.support;

import com.ai.mall.identity.application.port.AccessTokenIssuer;
import com.ai.mall.identity.infrastructure.security.RsaAccessTokenIssuer;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 测试环境令牌签发兜底（CHG-0016 STORY-02）。
 *
 * <p>生产 {@code JwtConfiguration} 带 {@code @Profile("!test")}，而会员令牌服务
 * （MemberTokenPairApplicationService，按 CHG-0016 新惯例不带 profile 开关）在所有测试
 * 上下文中都需要 {@link AccessTokenIssuer}。本自动配置：
 * <ul>
 *   <li>上下文已存在 JwtEncoder（如 ApiTestSecurityConfig/M1 安全切片）→ 复用之，保证与解码器同密钥；</li>
 *   <li>否则提供进程内临时 RSA 的 JwtEncoder（仓储/relay 等无安全链切片）。</li>
 * </ul>
 */
@AutoConfiguration
public class TestTokenIssuerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    JwtEncoder fallbackTestJwtEncoder() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Bean
    @ConditionalOnMissingBean(AccessTokenIssuer.class)
    AccessTokenIssuer fallbackTestAccessTokenIssuer(JwtEncoder encoder,
            @Value("${mall.security.jwt.issuer:ai-platform}") String issuer,
            @Value("${mall.security.jwt.audience:mall-admin-api}") String audience,
            @Value("${mall.security.jwt.access-ttl:PT15M}") Duration ttl) {
        return new RsaAccessTokenIssuer(encoder, Clock.systemUTC(), issuer, audience, ttl);
    }
}
