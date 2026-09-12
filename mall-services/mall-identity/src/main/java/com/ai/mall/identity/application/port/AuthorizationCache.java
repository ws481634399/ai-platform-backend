package com.ai.mall.identity.application.port;

import com.ai.mall.identity.application.dto.AuthorizationSnapshot;

import java.util.Optional;

public interface AuthorizationCache {
    Optional<AuthorizationSnapshot> get(long adminId, long permissionVersion);
    void put(AuthorizationSnapshot snapshot);
}
