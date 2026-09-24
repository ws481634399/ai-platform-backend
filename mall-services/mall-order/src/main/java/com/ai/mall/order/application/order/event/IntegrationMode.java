package com.ai.mall.order.application.order.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 集成投递模式判定（单开关权威来源）。
 *
 * <p>与 RocketMQAutoConfiguration 的总开关同属性：{@code rocketmq.enabled}=true 走异步事件路径，
 * false（缺省）走 M4 同步降级路径。所有需要区分两条路径的组件统一依赖本类，不自行读配置。
 */
@Component
public class IntegrationMode {

    private final boolean async;

    public IntegrationMode(@Value("${rocketmq.enabled:false}") boolean async) {
        this.async = async;
    }

    /** true=异步事件驱动；false=同步降级。 */
    public boolean async() {
        return async;
    }
}
