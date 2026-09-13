package com.ai.mall.identity.infrastructure.cache;

import com.ai.mall.common.security.AuthorizationKeys;
import com.ai.mall.identity.application.dto.AuthorizationSnapshot;
import com.ai.mall.identity.application.port.AuthorizationCache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuthorizationCache implements AuthorizationCache {
    private final ConcurrentHashMap<String, AuthorizationSnapshot> values = new ConcurrentHashMap<>();
    @Override public Optional<AuthorizationSnapshot> get(long adminId, long version) {
        return Optional.ofNullable(values.get(key(adminId, version)));
    }
    @Override public void put(AuthorizationSnapshot snapshot) {
        values.put(key(snapshot.adminId(), snapshot.permissionVersion()), snapshot);
    }
    /** 键规范单点定义在 mall-common-security AuthorizationKeys，保留委托方法兼容现有引用 */
    public static String key(long adminId, long version) {
        return AuthorizationKeys.snapshot(adminId, version);
    }
}
