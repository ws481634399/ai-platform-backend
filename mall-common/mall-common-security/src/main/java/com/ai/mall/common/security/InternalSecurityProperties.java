package com.ai.mall.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间内部接口安全属性（CHG-0015）。
 *
 * <p>配置前缀 {@code mall.security.internal}：
 * <ul>
 *   <li>{@code shared-secret}：共享凭证头 X-Internal-Token 的期望值，经环境变量
 *       {@code MALL_INTERNAL_SHARED_SECRET} 注入；仅本地开发使用默认值，生产缺失即启动失败（fail-fast）。</li>
 *   <li>{@code path}：内部凭证过滤器匹配的 Ant 路径，默认仅 {@code /api/internal/**}。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mall.security.internal")
public class InternalSecurityProperties {

    /** 服务间共享凭证；为空视为未配置（自动装配不启用过滤器）。 */
    private String sharedSecret;

    /** 过滤器保护路径前缀（Ant 风格）。 */
    private String path = "/api/internal/**";

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
