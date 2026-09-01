package com.ai.mall.common.web.config;

import com.ai.mall.common.web.advice.GlobalExceptionHandler;
import com.ai.mall.common.web.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web Foundation 自动装配：服务引入 mall-common-web 即零声明获得
 * TraceId 过滤器与全局异常处理（design.md §2.3"业务服务零额外声明"）。
 *
 * <p>@ConditionalOnMissingBean 允许服务按需覆盖；过滤器注册为最高优先级，
 * 保证任何组件打日志前 traceId 已写入 MDC。
 */
@AutoConfiguration
public class WebFoundationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
