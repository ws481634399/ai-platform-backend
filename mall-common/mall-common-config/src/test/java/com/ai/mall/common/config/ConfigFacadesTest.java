package com.ai.mall.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FeatureGate / SystemParameterProvider 门面语义单测（CHG-0022 DU-BE-508）：
 * 显式 false 拒绝（B0606）；缺键/故障按默认值；参数类型解析失败回退默认。
 */
class ConfigFacadesTest {

    private final SystemConfigClient client = mock(SystemConfigClient.class);

    @Test
    @DisplayName("FeatureGate：显式关闭 ensureEnabled 抛 FeatureDisabledException 带 featureKey（B0606）")
    void gateRejectsExplicitDisabled() {
        when(client.getFeature("search.enabled"))
                .thenReturn(Optional.of(new FeatureSnapshot("search.enabled", false, 2L)));
        FeatureGate gate = new FeatureGate(client);

        assertThat(gate.isEnabled("search.enabled")).isFalse();
        assertThatThrownBy(() -> gate.ensureEnabled("search.enabled"))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("search.enabled")
                .extracting(ex -> ((FeatureDisabledException) ex).getFeatureKey())
                .isEqualTo("search.enabled");
    }

    @Test
    @DisplayName("FeatureGate：显式开启放行")
    void gateAllowsExplicitEnabled() {
        when(client.getFeature("search.enabled"))
                .thenReturn(Optional.of(new FeatureSnapshot("search.enabled", true, 1L)));
        FeatureGate gate = new FeatureGate(client);

        assertThatCode(() -> gate.ensureEnabled("search.enabled")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FeatureGate：缺键/故障默认放行；可传入保守默认 false")
    void gateDefaults() {
        when(client.getFeature("new.feature")).thenReturn(Optional.empty());
        FeatureGate gate = new FeatureGate(client);

        assertThat(gate.isEnabled("new.feature")).isTrue();
        assertThatCode(() -> gate.ensureEnabled("new.feature")).doesNotThrowAnyException();
        assertThat(gate.isEnabled("new.feature", false)).isFalse();
        assertThatThrownBy(() -> gate.ensureEnabled("new.feature", false))
                .isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    @DisplayName("ParameterProvider：类型化解析；缺键/非法值回退默认")
    void providerFallbacks() {
        when(client.getParameter("p.int")).thenReturn(Optional.of(new ParameterSnapshot("p.int", " 25 ", "INTEGER", 1L)));
        when(client.getParameter("p.bad")).thenReturn(Optional.of(new ParameterSnapshot("p.bad", "abc", "INTEGER", 1L)));
        when(client.getParameter("p.miss")).thenReturn(Optional.empty());
        when(client.getParameter("p.dec")).thenReturn(Optional.of(new ParameterSnapshot("p.dec", "19.90", "DECIMAL", 1L)));
        when(client.getParameter("p.bool")).thenReturn(Optional.of(new ParameterSnapshot("p.bool", "1", "BOOLEAN", 1L)));
        SystemParameterProvider provider = new SystemParameterProvider(client);

        assertThat(provider.getInt("p.int", 20)).isEqualTo(25);
        assertThat(provider.getInt("p.bad", 20)).isEqualTo(20);
        assertThat(provider.getInt("p.miss", 20)).isEqualTo(20);
        assertThat(provider.getString("p.miss", "dflt")).isEqualTo("dflt");
        assertThat(provider.getLong("p.int", 0L)).isEqualTo(25L);
        assertThat(provider.getDecimal("p.dec", BigDecimal.ZERO)).isEqualByComparingTo("19.90");
        assertThat(provider.getBoolean("p.bool", false)).isTrue();
        assertThat(provider.getBoolean("p.miss", true)).isTrue();
    }

    @Test
    @DisplayName("ConfigErrorCode：B0606 为 FEATURE_DISABLED 冻结错误码")
    void errorCodeFrozen() {
        assertThat(ConfigErrorCode.FEATURE_DISABLED.getCode()).isEqualTo("B0606");
        assertThat(ConfigErrorCode.CONFIG_VALUE_INVALID.getCode()).isEqualTo("B0601");
        assertThat(ConfigErrorCode.CONFIG_NOT_FOUND.getCode()).isEqualTo("B0603");
        assertThat(ConfigErrorCode.CONFIG_VERSION_CONFLICT.getCode()).isEqualTo("B0604");
        assertThat(ConfigErrorCode.CONFIG_KEY_DUPLICATE.getCode()).isEqualTo("B0602");
    }
}
