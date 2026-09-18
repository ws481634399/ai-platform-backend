package com.ai.mall.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 系统配置客户端属性（CHG-0022）。
 *
 * <ul>
 *   <li>enabled：总开关，消费服务可在测试/特殊环境关闭整条客户端装配；</li>
 *   <li>systemBaseUri：mall-system 直连地址（默认 8108）；</li>
 *   <li>internalToken：X-Internal-Token 共享密钥（与 mall.security.internal.shared-secret 同值）；</li>
 *   <li>localTtlSeconds：进程内本地缓存 TTL（动态生效上限，冻结 60s）。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mall.config")
public class SystemConfigProperties {

    /** 客户端装配开关（缺省装配）。 */
    private boolean enabled = true;

    /** mall-system 基础地址。 */
    private String systemBaseUri = "http://localhost:8108";

    /** 服务间共享密钥。 */
    private String internalToken = "dev-internal-secret";

    /** 本地缓存 TTL 秒。 */
    private long localTtlSeconds = 60L;

    /** HTTP 连接超时（毫秒）。 */
    private long connectTimeoutMs = 1000L;

    /** HTTP 读超时（毫秒）。 */
    private long readTimeoutMs = 3000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSystemBaseUri() {
        return systemBaseUri;
    }

    public void setSystemBaseUri(String systemBaseUri) {
        this.systemBaseUri = systemBaseUri;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public long getLocalTtlSeconds() {
        return localTtlSeconds;
    }

    public void setLocalTtlSeconds(long localTtlSeconds) {
        this.localTtlSeconds = localTtlSeconds;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
