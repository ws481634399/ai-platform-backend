package com.ai.mall.identity.infrastructure.cache;

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
    public static String key(long adminId, long version) { return "authz:" + adminId + ":" + version; }
}
