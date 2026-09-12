package com.ai.mall.identity.application.port;

import com.ai.mall.identity.application.dto.AuthorizationSnapshot;

public interface AuthorizationRepository {
    AuthorizationSnapshot load(long adminId, long permissionVersion);
}
