package com.ai.mall.common.config;

/**
 * 功能开关关闭异常（CHG-0022 B0606）。
 *
 * <p>由 {@link FeatureGate} 在显式关闭时抛出；经自动配置的 advice 统一映射为
 * HTTP 403 + UnifyResult{code:"B0606"}。非 BusinessException 子类，避免消费服务
 * 额外依赖 mall-common-web 的异常体系（语义由本模块自持）。
 */
public class FeatureDisabledException extends RuntimeException {

    private final String featureKey;

    public FeatureDisabledException(String featureKey) {
        super(ConfigErrorCode.FEATURE_DISABLED.getMessage() + ": " + featureKey);
        this.featureKey = featureKey;
    }

    public String getFeatureKey() {
        return featureKey;
    }
}
