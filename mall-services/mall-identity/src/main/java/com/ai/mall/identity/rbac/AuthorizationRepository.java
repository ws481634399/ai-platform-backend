package com.ai.mall.identity.rbac;

public interface AuthorizationRepository {
    AuthorizationSnapshot load(long adminId, long permissionVersion);
}
