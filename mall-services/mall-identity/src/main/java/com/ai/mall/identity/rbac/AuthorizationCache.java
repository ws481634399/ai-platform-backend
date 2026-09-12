package com.ai.mall.identity.rbac;

import java.util.Optional;

public interface AuthorizationCache {
    Optional<AuthorizationSnapshot> get(long adminId, long permissionVersion);
    void put(AuthorizationSnapshot snapshot);
}
