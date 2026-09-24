package com.ai.mall.order.application.order.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ai.mall.common.config.SystemParameterProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PaymentTimeoutPolicy 单测（TC-002）：动态参数命中/缺键回退 yml 默认。 */
@ExtendWith(MockitoExtension.class)
class PaymentTimeoutPolicyTest {

    @Mock
    private SystemParameterProvider parameterProvider;

    @Test
    @DisplayName("系统参数存在时返回动态超时分钟数")
    void returnsDynamicMinutes() {
        when(parameterProvider.getLong("order.payment.timeout-minutes", 30L)).thenReturn(15L);
        PaymentTimeoutPolicy policy = new PaymentTimeoutPolicy(parameterProvider, 30L);

        assertThat(policy.timeoutMinutes()).isEqualTo(15L);
    }

    @Test
    @DisplayName("系统参数缺键/读取失败时回退 yml 默认值")
    void fallsBackToDefault() {
        when(parameterProvider.getLong("order.payment.timeout-minutes", 45L)).thenReturn(45L);
        PaymentTimeoutPolicy policy = new PaymentTimeoutPolicy(parameterProvider, 45L);

        assertThat(policy.timeoutMinutes()).isEqualTo(45L);
    }
}
