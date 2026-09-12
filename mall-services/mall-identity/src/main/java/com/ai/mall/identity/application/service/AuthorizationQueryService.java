package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.dto.AuthorizationSnapshot;
import com.ai.mall.identity.application.port.AuthorizationCache;
import com.ai.mall.identity.application.port.AuthorizationRepository;

import org.springframework.stereotype.Service;

@Service
public class AuthorizationQueryService {
    private final AuthorizationRepository repository;
    private final AuthorizationCache cache;

    public AuthorizationQueryService(AuthorizationRepository repository, AuthorizationCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public AuthorizationSnapshot get(long adminId, long permissionVersion) {
        try {
            var cached = cache.get(adminId, permissionVersion);
            if (cached.isPresent()) return cached.get();
        } catch (RuntimeException ignored) {
            // Redis is an optimization; database remains the authorization source of truth.
        }
        AuthorizationSnapshot loaded = repository.load(adminId, permissionVersion);
        if (loaded.adminId() != adminId || loaded.permissionVersion() != permissionVersion) {
            throw new IllegalStateException("authorization snapshot identity/version mismatch");
        }
        try { cache.put(loaded); } catch (RuntimeException ignored) { /* fail open only for cache, never DB */ }
        return loaded;
    }
}
