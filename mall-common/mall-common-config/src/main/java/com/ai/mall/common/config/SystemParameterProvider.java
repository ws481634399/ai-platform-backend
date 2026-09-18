package com.ai.mall.common.config;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统参数读取门面（CHG-0022）：缺键/类型不符/数值非法一律回退调用方默认值 + WARN。
 *
 * <p>WARN 限流：每个键每分钟最多一条，避免高频参数读取时日志风暴。
 */
public class SystemParameterProvider {

    private static final Logger log = LoggerFactory.getLogger(SystemParameterProvider.class);
    private static final long WARN_INTERVAL_MS = 60_000L;

    private final SystemConfigClient client;
    private final Map<String, Long> lastWarnAt = new ConcurrentHashMap<>();

    public SystemParameterProvider(SystemConfigClient client) {
        this.client = client;
    }

    public String getString(String key, String defaultValue) {
        return client.getParameter(key).map(ParameterSnapshot::value).orElseGet(() -> {
            warnOnce(key, "缺键或读取失败，使用默认值");
            return defaultValue;
        });
    }

    public int getInt(String key, int defaultValue) {
        ParameterSnapshot snapshot = client.getParameter(key).orElse(null);
        if (snapshot == null) {
            warnOnce(key, "缺键或读取失败，使用默认值");
            return defaultValue;
        }
        try {
            return Integer.parseInt(snapshot.value().trim());
        } catch (NumberFormatException ex) {
            warnOnce(key, "值不是合法 INTEGER: " + snapshot.value());
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        ParameterSnapshot snapshot = client.getParameter(key).orElse(null);
        if (snapshot == null) {
            warnOnce(key, "缺键或读取失败，使用默认值");
            return defaultValue;
        }
        try {
            return Long.parseLong(snapshot.value().trim());
        } catch (NumberFormatException ex) {
            warnOnce(key, "值不是合法 LONG: " + snapshot.value());
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        ParameterSnapshot snapshot = client.getParameter(key).orElse(null);
        if (snapshot == null) {
            warnOnce(key, "缺键或读取失败，使用默认值");
            return defaultValue;
        }
        try {
            return new BigDecimal(snapshot.value().trim());
        } catch (NumberFormatException ex) {
            warnOnce(key, "值不是合法 DECIMAL: " + snapshot.value());
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        ParameterSnapshot snapshot = client.getParameter(key).orElse(null);
        if (snapshot == null) {
            warnOnce(key, "缺键或读取失败，使用默认值");
            return defaultValue;
        }
        String raw = snapshot.value().trim();
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
            return false;
        }
        warnOnce(key, "值不是合法 BOOLEAN: " + raw);
        return defaultValue;
    }

    private void warnOnce(String key, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastWarnAt.get(key);
        if (last != null && now - last < WARN_INTERVAL_MS) {
            return;
        }
        lastWarnAt.put(key, now);
        log.warn("系统参数回退默认值 key={} reason={}", key, reason);
    }
}
