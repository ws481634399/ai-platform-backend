package com.ai.mall.common.security.config;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.security.InternalSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 服务间内部凭证自动装配（CHG-0015）：服务引入 mall-common-security 并配置
 * {@code mall.security.internal.shared-secret} 即获得 {@link InternalIdentityFilter} Bean，
 * 由各服务 SecurityFilterChain 以 addFilterBefore 方式接入。
 *
 * <p>通过 FilterRegistrationBean(setEnabled=false) 关闭 Boot 对该 Filter 的外层 servlet 注册，
 * 保证它只在 Spring Security 链内执行一次（OncePerRequestFilter 的去重标记才指向链内位置）。
 * 密钥为空白时直接 fail-fast，避免"忘配=裸奔"。
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(InternalSecurityProperties.class)
@ConditionalOnProperty(prefix = "mall.security.internal", name = "shared-secret")
public class InternalSecurityAutoConfiguration {

    @Bean
    InternalIdentityFilter internalIdentityFilter(InternalSecurityProperties properties) {
        String secret = properties.getSharedSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "mall.security.internal.shared-secret 必须配置非空值（环境变量 MALL_INTERNAL_SHARED_SECRET）");
        }
        return new InternalIdentityFilter(properties);
    }

    @Bean
    FilterRegistrationBean<InternalIdentityFilter> internalIdentityFilterRegistration(
            InternalIdentityFilter filter) {
        FilterRegistrationBean<InternalIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        // 仅在 SecurityFilterChain 内由 addFilterBefore 显式接入，禁止外层自动注册
        registration.setEnabled(false);
        return registration;
    }
}
