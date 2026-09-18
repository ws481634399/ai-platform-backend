package com.ai.mall.common.config;

import com.ai.mall.common.core.result.UnifyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 系统配置客户端自动配置（CHG-0022）。
 *
 * <p>消费服务引入 mall-common-config 即获得 {@link FeatureGate}、
 * {@link SystemParameterProvider}；{@code mall.config.enabled=false} 可整体关闭。
 * 同时注册 {@link FeatureDisabledException} → 403/B0606 的全局映射，
 * 保证跨服务错误结构统一。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mall.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SystemConfigProperties.class)
public class ConfigClientAutoConfiguration {

    @Bean
    SystemConfigClient systemConfigClient(SystemConfigProperties properties,
                                         StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        String env = profiles.length == 0 ? "dev" : profiles[0];
        return new SystemConfigClient(properties, redisTemplate, objectMapper, env);
    }

    @Bean
    FeatureGate featureGate(SystemConfigClient client) {
        return new FeatureGate(client);
    }

    @Bean
    SystemParameterProvider systemParameterProvider(SystemConfigClient client) {
        return new SystemParameterProvider(client);
    }

    /** B0606 统一映射：功能开关显式关闭 → HTTP 403。 */
    @RestControllerAdvice
    static class FeatureDisabledExceptionAdvice {

        @ExceptionHandler(FeatureDisabledException.class)
        ResponseEntity<UnifyResult<Void>> handle(FeatureDisabledException ex) {
            // 消息带 featureKey，便于前端/排查定位（异常超类消息已含 key）
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(UnifyResult.fail(ConfigErrorCode.FEATURE_DISABLED, ex.getMessage()));
        }
    }

    @Bean
    FeatureDisabledExceptionAdvice featureDisabledExceptionAdvice() {
        return new FeatureDisabledExceptionAdvice();
    }
}
