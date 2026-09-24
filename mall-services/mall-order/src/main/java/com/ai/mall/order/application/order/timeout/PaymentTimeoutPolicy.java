package com.ai.mall.order.application.order.timeout;

import com.ai.mall.common.config.SystemParameterProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单支付超时策略（CHG-0025 STORY-009-04-01）。
 *
 * <p>超时分钟数权威来源为 SystemParameter {@code order.payment.timeout-minutes}，
 * 经 M5 配置链路动态生效（本地 TTL 缓存，无需重启）；缺键/非法值由
 * {@link SystemParameterProvider} 回退 yml 默认值（order.payment.timeout-minutes，默认 30）。</p>
 */
@Component
public class PaymentTimeoutPolicy {

    /** 系统参数键：与 mall-system 参数种子及 ORDER_CREATED.paymentDeadline 口径一致。 */
    public static final String TIMEOUT_PARAM_KEY = "order.payment.timeout-minutes";

    private final SystemParameterProvider parameterProvider;
    private final long defaultMinutes;

    public PaymentTimeoutPolicy(SystemParameterProvider parameterProvider,
                                @Value("${order.payment.timeout-minutes:30}") long defaultMinutes) {
        this.parameterProvider = parameterProvider;
        this.defaultMinutes = defaultMinutes;
    }

    /** 当前支付超时分钟数（动态读取；调用方每次取到的都是缓存生效窗口内的最新值）。 */
    public long timeoutMinutes() {
        return parameterProvider.getLong(TIMEOUT_PARAM_KEY, defaultMinutes);
    }
}
